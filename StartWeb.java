import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class StartWeb {

    private static final String LOW_MEMORY_JVM_ARGS =
            "-Xms32m -Xmx320m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k";

    public static void main(String[] args)
            throws IOException, InterruptedException, URISyntaxException {

        File projectDir = new File(StartWeb.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        if (projectDir.isFile()) {
            projectDir = projectDir.getParentFile();
        }

        String mavenCommand = resolveMavenCommand(projectDir);

        if (isPortOpen(8080)) {
            System.out.println("HR Management is already running at http://localhost:8080/login");
            return;
        }

        System.out.println("Starting HR Management...");
        System.out.println("Open your browser at: http://localhost:8080/login");

        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe",
                "/d",
                "/c",
                "\"" + mavenCommand + "\" -DskipTests spring-boot:run"
        );

        processBuilder.directory(projectDir);
        processBuilder.environment().put("MAVEN_OPTS", LOW_MEMORY_JVM_ARGS);
        processBuilder.inheritIO();

        Process process = processBuilder.start();
        process.waitFor();
    }

    private static boolean isPortOpen(int port) {

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 600);
            return true;

        } catch (IOException e) {
            return false;
        }
    }

    private static String resolveMavenCommand(File projectDir) {

        Optional<File> downloadedMaven = findDownloadedMaven();

        if (downloadedMaven.isPresent()) {
            return downloadedMaven.get().getAbsolutePath();
        }

        return new File(projectDir, "mvnw.cmd").getAbsolutePath();
    }

    private static Optional<File> findDownloadedMaven() {

        String userHome = System.getProperty("user.home");
        File distsDir = new File(userHome, ".m2/wrapper/dists");

        if (!distsDir.isDirectory()) {
            return Optional.empty();
        }

        return findFiles(distsDir)
                .filter(file -> "mvn.cmd".equalsIgnoreCase(file.getName()))
                .max(Comparator.comparing(File::getAbsolutePath));
    }

    private static Stream<File> findFiles(File root) {

        File[] files = root.listFiles();

        if (files == null) {
            return Stream.empty();
        }

        return Stream.of(files)
                .flatMap(file -> file.isDirectory() ? findFiles(file) : Stream.of(file));
    }
}
