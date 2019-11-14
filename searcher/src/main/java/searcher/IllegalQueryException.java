package searcher;

public class IllegalQueryException extends Throwable {

    public final String query;

    public IllegalQueryException(String query) {
        this.query = query;
    }
}
