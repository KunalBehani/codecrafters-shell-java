import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
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
        List<String> args = new ArrayList<>();
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
            String outputFile = null;
            String errorFile = null;
            List<String> cleanParts = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals(">") || parts[i].equals("1>")) {
                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                    }
                    i++;
                } else if (parts[i].equals("2>")) {
                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                    }
                    i++;
                } else {
                    cleanParts.add(parts[i]);
                }
            }
            parts = cleanParts.toArray(new String[0]);
            if (parts.length == 0) {
                continue;
            }
            if (command.equals("exit") || command.equals("exit 0")) {
                break;
            }
            else if (parts[0].equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(parts[i]);
                }
                if (outputFile != null) {
                    try {
                        PrintWriter writer = new PrintWriter(outputFile);
                        writer.println(output);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(output);
                }
            }
            else if (parts[0].equals("pwd")) {
                if (outputFile != null) {
                    try {
                        PrintWriter writer = new PrintWriter(outputFile);
                        writer.println(currentDirectory);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(currentDirectory);
                }
            }
            else if (parts[0].equals("cd")) {
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
                        String errMsg = "cd: " + path + ": No such file or directory";
                        if (errorFile != null) {
                            try (PrintWriter writer = new PrintWriter(errorFile)) {
                                writer.println(errMsg);
                            } catch (Exception ignored) {
                            }
                        } else {
                            System.err.println(errMsg);
                        }
                    }
                } else {
                    String errMsg = "cd: " + path + ": No such file or directory";
                    if (errorFile != null) {
                        try (PrintWriter writer = new PrintWriter(errorFile)) {
                            writer.println(errMsg);
                        } catch (Exception ignored) {
                        }
                    } else {
                        System.err.println(errMsg);
                    }
                }
            }
            else if (parts[0].equals("type")) {
                if (parts.length < 2) {
                    continue;
                }
                String cmd = parts[1];
                String result;
                if (builtins.contains(cmd)) {
                    result = cmd + " is a shell builtin";
                } else {
                    File executable = findExecutable(cmd);
                    if (executable != null) {
                        result = cmd + " is " + executable.getAbsolutePath();
                    } else {
                        result = cmd + ": not found";
                    }
                }
                if (outputFile != null) {
                    try {
                        PrintWriter writer = new PrintWriter(outputFile);
                        writer.println(result);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(result);
                }
            }
            else {
                File executable = findExecutable(parts[0]);
                if (executable == null) {
                    String errMsg = command + ": command not found";
                    if (errorFile != null) {
                        try (PrintWriter writer = new PrintWriter(errorFile)) {
                            writer.println(errMsg);
                        } catch (Exception ignored) {
                        }
                    } else {
                        System.err.println(errMsg);
                    }
                } else {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(currentDirectory));
                        if (outputFile != null) {
                            pb.redirectOutput(new File(outputFile));
                        }
                        if (errorFile != null) {
                            pb.redirectError(new File(errorFile));
                        }
                        Process process = pb.start();
                        if (outputFile == null) {
                            process.getInputStream().transferTo(System.out);
                        }
                        if (errorFile == null) {
                            process.getErrorStream().transferTo(System.err);
                        }
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