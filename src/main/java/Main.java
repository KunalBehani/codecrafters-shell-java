import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {

    private static File findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String currentDirectory = System.getProperty("user.dir");

        Set<String> builtins = Set.of(
                "echo",
                "exit",
                "type",
                "pwd",
                "cd"
        );

        while (true) {
            System.out.print("$ ");

            if (!sc.hasNextLine()) {
                break;
            }

            String command = sc.nextLine();

            // Support both old and new Codecrafters stages
            if (command.equals("exit") || command.equals("exit 0")) {
                break;
            }
            else if (command.startsWith("echo ")) {
                System.out.println(command.substring(5));
            }
            else if (command.equals("pwd")) {
                System.out.println(currentDirectory);
            }
            else if (command.startsWith("cd ")) {
                String path = command.substring(3);

                File dir = new File(path);

                if (dir.exists() && dir.isDirectory()) {
                    currentDirectory = dir.getAbsolutePath();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }
            else if (command.startsWith("type ")) {
                String cmd = command.substring(5);

                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    File executable = findExecutable(cmd);

                    if (executable != null) {
                        System.out.println(cmd + " is " + executable.getAbsolutePath());
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            }
            else {
                String[] parts = command.split(" ");

                File executable = findExecutable(parts[0]);

                if (executable == null) {
                    System.out.println(command + ": command not found");
                } else {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(currentDirectory));

                        Process process = pb.start();

                        process.getInputStream().transferTo(System.out);
                        process.getErrorStream().transferTo(System.err);

                        process.waitFor();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        sc.close();
    }
}