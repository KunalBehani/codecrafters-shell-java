import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {
    static class Job {
        int jobNumber;
        long pid;
        String command;
        Process process;
        boolean doneShown;

        Job(int jobNumber, long pid, String command, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.process = process;
            this.doneShown = false;
        }
    }

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

    private static String executeBuiltinForPipeline(String[] parts, Set<String> builtins) {

        if (parts.length == 0) {
            return "";
        }

        if (parts[0].equals("echo")) {

            StringBuilder sb = new StringBuilder();

            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    sb.append(" ");
                }
                sb.append(parts[i]);
            }

            sb.append("\n");
            return sb.toString();
        }

        if (parts[0].equals("type")) {

            if (parts.length < 2) {
                return "";
            }

            String cmd = parts[1];

            if (builtins.contains(cmd)) {
                return cmd + " is a shell builtin\n";
            }

            File executable = findExecutable(cmd);

            if (executable != null) {
                return cmd + " is " + executable.getAbsolutePath() + "\n";
            }

            return cmd + ": not found\n";
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

    private static void reapJobs(List<Job> jobsList) {

        List<Job> jobsToRemove = new ArrayList<>();

        List<Job> visibleJobs = new ArrayList<>();

        for (Job job : jobsList) {
            if (job.process.isAlive() || !job.doneShown) {
                visibleJobs.add(job);
            }
        }

        for (int i = 0; i < visibleJobs.size(); i++) {

            Job job = visibleJobs.get(i);

            if (job.process.isAlive()) {
                continue;
            }

            char marker = ' ';

            if (visibleJobs.size() == 1) {
                marker = '+';
            } else if (i == visibleJobs.size() - 1) {
                marker = '+';
            } else if (i == visibleJobs.size() - 2) {
                marker = '-';
            }

            System.out.printf("[%d]%c  %-24s%s%n",
                    job.jobNumber,
                    marker,
                    "Done",
                    job.command.replaceAll("\\s*&\\s*$", ""));

            job.doneShown = true;
            jobsToRemove.add(job);
        }

        jobsList.removeAll(jobsToRemove);
    }

    private static int getNextJobNumber(List<Job> jobsList) {

        if (jobsList.isEmpty()) {
            return 1;
        }

        int maxJobNumber = 0;

        for (Job job : jobsList) {
            maxJobNumber = Math.max(maxJobNumber, job.jobNumber);
        }

        return maxJobNumber + 1;
    }

    private static void executePipeline(
            String command,
            String currentDirectory,
            Set<String> builtins) {

        try {

            String[] pipelineParts = command.split("\\|");

            if (pipelineParts.length == 2) {

                String[] left = parseCommand(pipelineParts[0].trim());

                String[] right = parseCommand(pipelineParts[1].trim());

                String leftBuiltin = executeBuiltinForPipeline(left, builtins);

                String rightBuiltin = executeBuiltinForPipeline(right, builtins);

                if (leftBuiltin != null) {

                    ProcessBuilder pb = new ProcessBuilder(right);

                    pb.directory(new File(currentDirectory));

                    Process process = pb.start();

                    process.getOutputStream()
                            .write(leftBuiltin.getBytes());

                    process.getOutputStream().close();

                    process.getInputStream()
                            .transferTo(System.out);

                    process.waitFor();
                    return;
                }

                if (rightBuiltin != null) {

                    ProcessBuilder pb = new ProcessBuilder(left);

                    pb.directory(new File(currentDirectory));

                    Process process = pb.start();

                    process.waitFor();

                    System.out.print(rightBuiltin);
                    return;
                }
            }

            List<ProcessBuilder> builders = new ArrayList<>();

            for (String part : pipelineParts) {

                String[] cmd = parseCommand(part.trim());

                ProcessBuilder pb = new ProcessBuilder(cmd);

                pb.directory(new File(currentDirectory));

                builders.add(pb);
            }

            List<Process> processes = ProcessBuilder.startPipeline(builders);

            Process last = processes.get(processes.size() - 1);

            last.getInputStream().transferTo(System.out);
            last.getErrorStream().transferTo(System.err);

            for (Process p : processes) {
                p.waitFor();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        String currentDirectory = System.getProperty("user.dir");
        List<Job> jobsList = new ArrayList<>();

        Set<String> builtins = Set.of(
                "echo",
                "exit",
                "type",
                "pwd",
                "cd",
                "jobs");

        while (true) {

            reapJobs(jobsList);

            System.out.print("$ ");

            if (!sc.hasNextLine()) {
                break;
            }

            String command = sc.nextLine();

            if (command.contains("|")) {
                executePipeline(command, currentDirectory, builtins);
                continue;
            }
            String[] parts = parseCommand(command);

            String outputFile = null;
            String errorFile = null;
            boolean appendOutput = false;
            boolean appendError = false;
            List<String> cleanParts = new ArrayList<>();

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals(">") || parts[i].equals("1>")) {
                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = false;
                    }
                    i++;
                } else if (parts[i].equals(">>") || parts[i].equals("1>>")) {
                    if (i + 1 < parts.length) {
                        outputFile = parts[i + 1];
                        appendOutput = true;
                    }
                    i++;
                } else if (parts[i].equals("2>")) {
                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = false;
                    }
                    i++;
                } else if (parts[i].equals("2>>")) {
                    if (i + 1 < parts.length) {
                        errorFile = parts[i + 1];
                        appendError = true;
                    }
                    i++;
                } else {
                    cleanParts.add(parts[i]);
                }
            }

            parts = cleanParts.toArray(new String[0]);
            boolean backgroundJob = false;

            if (parts.length > 0 && parts[parts.length - 1].equals("&")) {
                backgroundJob = true;

                String[] newParts = new String[parts.length - 1];
                System.arraycopy(parts, 0, newParts, 0, parts.length - 1);
                parts = newParts;
            }

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
                        PrintWriter writer = new PrintWriter(
                                new FileOutputStream(outputFile, appendOutput));
                        writer.println(output);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(output);
                }

                if (errorFile != null) {
                    try (PrintWriter writer = new PrintWriter(
                            new FileOutputStream(errorFile, appendError))) {
                        // create empty stderr file
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            else if (parts[0].equals("pwd")) {
                if (outputFile != null) {
                    try {
                        PrintWriter writer = new PrintWriter(
                                new FileOutputStream(outputFile, appendOutput));
                        writer.println(currentDirectory);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(currentDirectory);
                }

                if (errorFile != null) {
                    try (PrintWriter writer = new PrintWriter(
                            new FileOutputStream(errorFile, appendError))) {
                        // create empty stderr file
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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
            } else if (parts[0].equals("jobs")) {

                List<Job> visibleJobs = new ArrayList<>();
                List<Job> jobsToRemove = new ArrayList<>();

                for (Job job : jobsList) {
                    if (job.process.isAlive() || !job.doneShown) {
                        visibleJobs.add(job);
                    }
                }

                for (int i = 0; i < visibleJobs.size(); i++) {

                    Job job = visibleJobs.get(i);

                    char marker = ' ';

                    if (visibleJobs.size() == 1) {
                        marker = '+';
                    } else if (i == visibleJobs.size() - 1) {
                        marker = '+';
                    } else if (i == visibleJobs.size() - 2) {
                        marker = '-';
                    }

                    if (job.process.isAlive()) {

                        System.out.printf("[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Running",
                                job.command);

                    } else {

                        System.out.printf("[%d]%c  %-24s%s%n",
                                job.jobNumber,
                                marker,
                                "Done",
                                job.command.replaceAll("\\s*&\\s*$", ""));

                        job.doneShown = true;
                        jobsToRemove.add(job);
                    }
                }

                jobsList.removeAll(jobsToRemove);
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
                        PrintWriter writer = new PrintWriter(
                                new FileOutputStream(outputFile, appendOutput));
                        writer.println(result);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println(result);
                }

                if (errorFile != null) {
                    try (PrintWriter writer = new PrintWriter(
                            new FileOutputStream(errorFile, appendError))) {
                        // create empty stderr file
                    } catch (Exception e) {
                        e.printStackTrace();
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

                        if (backgroundJob) {

                            pb.inheritIO();

                            Process process = pb.start();

                            int jobNumber = getNextJobNumber(jobsList);

                            Job job = new Job(
                                    jobNumber,
                                    process.pid(),
                                    command,
                                    process);

                            jobsList.add(job);

                            System.out.println("[" + jobNumber + "] " + process.pid());
                        } else {

                            Process process = pb.start();

                            if (outputFile != null) {
                                FileOutputStream fos = new FileOutputStream(outputFile, appendOutput);
                                process.getInputStream().transferTo(fos);
                                fos.close();
                            } else {
                                process.getInputStream().transferTo(System.out);
                            }

                            if (errorFile != null) {
                                try (FileOutputStream errFos = new FileOutputStream(errorFile, appendError)) {
                                    process.getErrorStream().transferTo(errFos);
                                }
                            } else {
                                process.getErrorStream().transferTo(System.err);
                            }

                            process.waitFor();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        sc.close();
    }
}