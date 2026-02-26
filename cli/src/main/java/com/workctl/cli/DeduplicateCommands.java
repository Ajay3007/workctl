package com.workctl.cli;

import com.workctl.core.domain.CommandEntry;
import com.workctl.core.service.CommandService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeduplicateCommands {
    public static void main(String[] args) throws Exception {
        CommandService service = new CommandService();
        List<CommandEntry> allCommands = service.loadAllCommands();

        // Track seen commands by category + title + project
        Set<String> seen = new HashSet<>();
        int deletedCount = 0;

        for (CommandEntry cmd : allCommands) {
            String key = cmd.getCategory() + "|" + cmd.getTitle() + "|" + cmd.getProjectTag();
            if (seen.contains(key)) {
                service.deleteCommand(cmd);
                System.out.println("Deleted duplicate: " + cmd.getTitle() + " [" + cmd.getCategory() + "]");
                deletedCount++;
            } else {
                seen.add(key);
            }
        }

        System.out.println("Finished. Deleted " + deletedCount + " duplicate commands.");
    }
}
