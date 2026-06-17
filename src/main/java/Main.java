import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    private static String[] parseCommand(String input) {
        java.util.List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append('\\');
                    }
                } else if (c == '"') {
                    inDoubleQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\\' && i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                } else if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String currentDirectory = System.getProperty("user.dir");

        Set<String> builtins = Set.of(
                "echo",
                "exit",
                "type",
                "pwd",
                "cd");

        while (true) {
            System.out.print("$ ");

            if (!sc.hasNextLine()) {
                break;
            }

            String command = sc.nextLine();
            String[] parts = parseCommand(command);

            if (parts.length == 0) {
                continue;
            }

            if (command.equals("exit") || command.equals("exit 0")) {
                break;
            } else if (parts[0].equals("echo")) {
                StringBuilder output = new StringBuilder();

                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(parts[i]);
                }

                System.out.println(output);
            } else if (parts[0].equals("pwd")) {
                System.out.println(currentDirectory);
            } else if (parts[0].equals("cd")) {
                if (parts.length < 2) {
                    continue;
                }

                String path = parts[1];

                File target;

                if (path.equals("~")) {
                    target = new File(System.getenv("HOME"));
                } else if (path.startsWith("/")) {
                    target = new File(path);
                } else {
                    target = new File(currentDirectory, path);
                }

                if (target.exists() && target.isDirectory()) {
                    try {
                        currentDirectory = target.getCanonicalPath();
                    } catch (Exception e) {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            } else if (parts[0].equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String cmd = parts[1];

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
            } else {
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