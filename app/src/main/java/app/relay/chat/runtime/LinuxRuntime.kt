package app.relay.chat.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * A private Alpine Linux userland run through proot (no root: proot fakes the filesystem
 * with ptrace). Lives entirely in the app's files dir:
 *
 *   files/rt/      proot + loader + libs, unpacked from assets/runtime/<arch>
 *   files/alpine/  Alpine minirootfs + whatever the CLIs install
 *   files/tmp/     bound to /tmp inside
 *
 * Needs targetSdk ≤ 28 so the app may exec files it wrote (see build.gradle.kts).
 */
class LinuxRuntime(private val context: Context) {
    // ARM64 only, judged by the primary ABI (x86_64 emulators also list arm64-v8a via
    // translation). On x86_64, Android's app seccomp policy rejects the legacy fork syscall
    // that musl uses there, so nothing inside Alpine can start a child process.
    val arch: String? = if (Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") "aarch64" else null

    private val files = context.filesDir
    private val rt = File(files, "rt")
    val rootfs = File(files, "alpine")
    val tmp = File(files, "tmp")
    private val baseMarker = File(rootfs, ".relay-base-v1")

    val isBaseReady get() = baseMarker.exists()

    /** Path inside the rootfs, e.g. "/usr/local/bin/codex" → files/alpine/usr/local/bin/codex. */
    fun hostPath(path: String) = File(rootfs, path.removePrefix("/"))

    /** Unpacks proot, downloads Alpine and installs the packages the CLIs need. Idempotent. */
    suspend fun ensureBase(log: (String) -> Unit) = withContext(Dispatchers.IO) {
        val arch = arch ?: throw IOException("Needs an ARM64 phone.")
        unpackProot(arch)
        if (isBaseReady) return@withContext

        if (!File(rootfs, "etc/alpine-release").exists()) {
            val tarball = File(files, "alpine.tar.gz")
            log("Downloading Alpine Linux ($ALPINE_VERSION)…")
            download("$ALPINE_MIRROR/v${ALPINE_VERSION.substringBeforeLast('.')}/releases/$arch/alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz", tarball, log)
            log("Unpacking…")
            rootfs.mkdirs()
            val tar = ProcessBuilder("/system/bin/tar", "xzf", tarball.absolutePath, "-C", rootfs.absolutePath)
                .redirectErrorStream(true).start()
            val out = tar.inputStream.bufferedReader().readText()
            if (tar.waitFor() != 0 || !File(rootfs, "etc/alpine-release").exists()) {
                throw IOException("Couldn't unpack Alpine: ${out.take(300)}")
            }
            tarball.delete()
        }

        log("Installing base packages…")
        val code = run(
            "apk update && apk add --no-progress bash curl ca-certificates libgcc libstdc++ ripgrep python3 py3-pip",
            log,
        )
        if (code != 0) throw IOException("apk failed (exit $code) — check your internet connection.")

        // Hand browser launches to Relay: the CLIs call xdg-open / $BROWSER for sign-in, and
        // this shim writes the URL where the app can see it (files/tmp is /tmp inside).
        hostPath("/usr/local/bin/relay-open").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\nprintf '%s\\n' \"\$1\" >> /tmp/relay-open-url\necho \"Open this URL: \$1\"\n")
            setExecutable(true, false)
        }
        hostPath("/usr/local/bin/xdg-open").apply {
            writeText("#!/bin/sh\nexec /usr/local/bin/relay-open \"\$@\"\n")
            setExecutable(true, false)
        }
        baseMarker.writeText(arch)
        log("Linux runtime ready.")
    }

    private fun unpackProot(arch: String) {
        rt.mkdirs()
        tmp.mkdirs()
        // Re-copied when the app is updated (assets may carry a newer proot).
        val stamp = File(rt, ".stamp")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.exists() && stamp.readText() == version) return
        for (name in listOf("proot", "loader", "libtalloc.so.2", "libandroid-shmem.so")) {
            val dst = File(rt, name)
            context.assets.open("runtime/$arch/$name").use { input -> dst.outputStream().use { input.copyTo(it) } }
            dst.setExecutable(true, true)
        }
        stamp.writeText(version)
    }

    /** DNS for musl: Android has no /etc/resolv.conf, so write the active network's servers. */
    private fun writeResolvConf() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        // The phone's own resolvers first (IPv4 before IPv6); link-local IPv6 needs a scope id
        // musl can't use, so skip it. Public resolvers fill in (musl reads at most 3).
        val servers = runCatching {
            cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty()
                .filterNot { it.isLinkLocalAddress }
                .sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
                .mapNotNull { it.hostAddress }
        }.getOrNull().orEmpty()
        val lines = (servers + listOf("1.1.1.1", "8.8.8.8")).distinct().take(3)
        hostPath("/etc/resolv.conf").writeText(lines.joinToString("") { "nameserver $it\n" })
    }

    /**
     * Starts [argv] inside the rootfs. [env] adds variables to a clean environment.
     * stdout/stderr are separate unless [mergeStderr].
     */
    fun start(argv: List<String>, env: Map<String, String> = emptyMap(), workdir: String = "/root", mergeStderr: Boolean = false): Process {
        unpackProot(arch ?: throw IOException("Unsupported CPU"))
        writeResolvConf()
        hostPath(workdir).mkdirs()
        val cmd = mutableListOf(
            File(rt, "proot").absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "${tmp.absolutePath}:/tmp",
            "-w", workdir,
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "TERM=dumb",
            "BROWSER=/usr/local/bin/relay-open",
            "USE_BUILTIN_RIPGREP=0",
            "DISABLE_AUTOUPDATER=1",
        )
        env.forEach { (k, v) -> cmd += "$k=$v" }
        cmd += argv
        return ProcessBuilder(cmd).apply {
            environment().apply {
                clear()
                put("LD_LIBRARY_PATH", rt.absolutePath)
                put("PROOT_LOADER", File(rt, "loader").absolutePath)
                put("PROOT_TMP_DIR", tmp.absolutePath)
                put("HOME", files.absolutePath)
            }
            redirectErrorStream(mergeStderr)
        }.start()
    }

    /** Runs a shell command, streaming merged output lines to [log]; returns the exit code. */
    suspend fun run(command: String, log: (String) -> Unit, env: Map<String, String> = emptyMap()): Int = withContext(Dispatchers.IO) {
        val p = start(listOf("/bin/sh", "-c", command), env, mergeStderr = true)
        try {
            p.inputStream.forEachLine { line -> if (line.isNotBlank()) log(stripAnsi(line)) }
            p.waitFor()
        } finally {
            p.destroy()
        }
    }

    private val http = OkHttpClient()

    private suspend fun download(url: String, dst: File, log: (String) -> Unit) {
        val resp = http.newCall(Request.Builder().url(url).build()).execute()
        resp.use {
            if (!it.isSuccessful) throw IOException("Download failed: HTTP ${it.code} for $url")
            val body = it.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            dst.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct / 10 != lastPct / 10) { lastPct = pct; log("  $pct% of ${total / 1_000_000} MB") }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ALPINE_VERSION = "3.24.2"
        const val ALPINE_MIRROR = "https://dl-cdn.alpinelinux.org/alpine"
        private val ANSI = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]|\u001B\][^\u0007]*\u0007""")
        fun stripAnsi(s: String) = s.replace(ANSI, "")
    }
}

/** Reads lines split on \n or \r (curl/progress output uses bare \r). */
fun InputStream.forEachLine(block: (String) -> Unit) {
    val r = bufferedReader()
    val sb = StringBuilder()
    while (true) {
        val c = r.read()
        if (c < 0) break
        if (c == '\n'.code || c == '\r'.code) {
            if (sb.isNotEmpty()) block(sb.toString())
            sb.clear()
        } else sb.append(c.toChar())
    }
    if (sb.isNotEmpty()) block(sb.toString())
}
