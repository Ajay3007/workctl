package com.workctl.cli.util;

/**
 * Background-thread spinner that overwrites the current line via {@code \r}.
 *
 * <pre>
 *   CliSpinner spinner = new CliSpinner("Thinking");
 *   spinner.start();
 *   String result = someBlockingCall();
 *   spinner.stop();   // clears the spinner line
 * </pre>
 *
 * Automatically skips rendering when output is piped (no console).
 */
public class CliSpinner {

    private static final String[] FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final String   label;
    private volatile boolean running;
    private Thread         thread;

    public CliSpinner(String label) {
        this.label = label;
    }

    /** Start the spinner in a daemon background thread. No-op when piped. */
    public void start() {
        if (System.console() == null) return;
        running = true;
        thread = new Thread(() -> {
            int i = 0;
            while (running) {
                System.out.print("\r" + FRAMES[i % FRAMES.length] + " " + label + "...");
                System.out.flush();
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** Stop the spinner and clear its line. */
    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (System.console() != null) {
            // Overwrite with spaces then return cursor to start of line
            System.out.print("\r" + " ".repeat(label.length() + 12) + "\r");
            System.out.flush();
        }
    }
}
