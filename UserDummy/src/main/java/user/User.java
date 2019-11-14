package user;

import indexer.TextIndexer;
import searcher.SearchCommand;

public class User {

    public static void main(String args[]) {
       // TextIndexer ind = new TextIndexer(TextIndexer.Mode.PURGE);
       // ind.startIndexer();
       // ind = new TextIndexer(TextIndexer.Mode.ADD, "/home/jakub/lucyna_test");
       // ind.startIndexer();
        TextIndexer ind = new TextIndexer(TextIndexer.Mode.REINDEX);
        ind.startIndexer();

        SearchCommand s = new SearchCommand();
        s.commandControl();
    }
}
