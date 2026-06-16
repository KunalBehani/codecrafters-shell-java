import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type");

        while (true) {
            System.out.print("$ ");

            String input = sc.nextLine();
            String[] parts = input.split(" ");

            if (parts[0].equals("exit")) {
                break;
            }

            if (parts[0].equals("echo")) {
                System.out.println(input.substring(5));
            }
            else if (parts[0].equals("type")) {
                String cmd = parts[1];

                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    File exe = findExecutable(cmd);

                    if (exe != null) {
                        System.out.println(cmd + " is " + exe.getAbsolutePath());
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            }
            else {
                File exe = findExecutable(parts[0]);

                if (exe != null) {
                    parts[0] = exe.getAbsolutePath();

                    Process process = new ProcessBuilder(parts).start();

                    process.getInputStream().transferTo(System.out);
                    process.waitFor();
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }

    static File findExecutable(String command) {
        String path = System.getenv("PATH");

        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, command);

            if (file.exists() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }
}