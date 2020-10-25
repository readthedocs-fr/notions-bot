package tech.hokkaydo.notionsbot.command.manager;

/**
 * Created by Hokkaydo on 05-09-2020.
 */
public interface Command {
    void executeCommand(CommandContext commandContext);
    String getName();
}
