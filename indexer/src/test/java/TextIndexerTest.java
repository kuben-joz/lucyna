import indexer.TextIndexer;
import org.junit.jupiter.api.Test;

class TextIndexerTest {

    //remove main methods
    @Test
    void startIndexer() {
        TextIndexer ind = new TextIndexer(TextIndexer.Mode.ADD, "./");
        ind.startIndexer();
    }

    @Test
    void test2() {
        TextIndexer ind = new TextIndexer(TextIndexer.Mode.MONITOR);
        ind.startIndexer();
    }

    @Test
    void reindex() {
        TextIndexer ind = new TextIndexer(TextIndexer.Mode.REINDEX);
        ind.startIndexer();
    }
}