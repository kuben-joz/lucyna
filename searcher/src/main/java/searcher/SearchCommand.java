package searcher;

import org.jline.builtins.Completers;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.Scanner;

public class SearchCommand {
    protected Terminal terminal;

    public void commandControl() {


        boolean configChanged = true;
        SearchExecutor searcher = null;
        Scanner commandReader = new Scanner(System.in);
        SearchBuilder builder = SearchBuilder.getBuilder();
        try {
            terminal = TerminalBuilder.builder().jansi(true).jna(false).build();
        } catch (IOException e) {
            System.err.println("Cannot open jansi terminal, exiting");
            return;
        }
        LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).
                completer(new Completers.FileNameCompleter()).build();


        while(true) {
            //System.out.print("> ");
            String command;
            try {
                command = lineReader.readLine("> ");
            }catch(UserInterruptException e) {
                terminal.writer().println("SIGINT detected, closing down");
                return;
            }
            //String command = commandReader.nextLine();
            //TODO add default
            if(command.charAt(0) == '%') { //because of "> "
                String[] commandSplit = command.split(" ");
                switch (commandSplit[0]) {
                    case "%lang":
                        boolean result = builder.setLang(commandSplit[1]);
                        configChanged = result || configChanged;
                        break;
                    case "%details":
                            if(commandSplit.length != 2) badCommand(command); //TODO for all
                            else {
                                result = builder.setDetails(commandSplit[1]);
                                configChanged = result || configChanged;
                                System.out.println("changed");
                            }
                            break;
                    case "%limit":
                        result = builder.setLimit(commandSplit[1]);
                        configChanged = result || configChanged;
                        break;
                    case "%color":
                        result = builder.setColor(commandSplit[1]);
                        configChanged = result || configChanged;
                        break;
                    case "%term":
                        result = builder.setSearchMode(commandSplit[0]);
                        configChanged = result || configChanged;
                        break;
                    case "%phrase":
                        result = builder.setSearchMode(commandSplit[0]);
                        configChanged = result || configChanged;
                        break;
                    case "%fuzzy":
                        result = builder.setSearchMode(commandSplit[0]);
                        configChanged = result || configChanged;
                        break;
                    default:
                        badCommand(command);
                }
            }
            else {
                if(configChanged) {
                  searcher = builder.updateConfig(searcher);
                  configChanged = false;
                }
                searcher.search(command, terminal);
            }
        }
    }

    private void badCommand(String command) {
        System.err.println("Command " + command + " does not exist");
    }


}
