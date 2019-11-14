package searcher;

class SearchBuilder {
    //private all stuff
    private static SearchBuilder builderSingleton = null;


    enum Language {EN, PL}
    private Language chosenLang = Language.EN;
    private boolean details = false;
    private int searchLimit = Integer.MAX_VALUE;
    private boolean color = false;
    enum SearchMode {TERM, PHRASE, FUZZY}
    SearchMode searchMode = SearchMode.TERM;

    //to lower case
    boolean setLang(String s) {
        Language previous = chosenLang;
        if(s.equals("EN")) {
            chosenLang = Language.EN;
            return chosenLang != previous;
        }
        else if(s.equals("PL")) {
            chosenLang = Language.PL;
            return chosenLang != previous;
        }
        else {
            System.err.println("Invalid language setting");
            return false;
        }
    }

    boolean setDetails(String s) {
        boolean previous = details;
        if(s.equals("on")) {
            details = true;
            return details != previous;
        }
        else if(s.equals("off")) {
            details = false;
            return details != previous;
        }
        else {
            System.err.println("Invalid details settings");
            return false;
        }
    }

    boolean setLimit(String s) {
        int temp = 0;
        int previous = searchLimit;
        try {
            temp = Integer.parseInt(s);
        }catch (NumberFormatException e) {
            System.err.println("Illegal result limit argument");
            e.printStackTrace();
            return false;
        }
        if(temp < 0) {
           System.err.println("Illegal result limit " + temp);
           return false;
        }
        else if(temp == 0) {
            searchLimit = Integer.MAX_VALUE;
            return searchLimit != previous;
        }
        else {
            searchLimit = temp;
            return searchLimit != previous;
        }
    }

    boolean setColor(String s) {
        boolean previous = color;
        if(s.equals("on")) {
            color = true;
            return color != previous;
        }
        else if(s.equals("off")) {
            color = false;
            return color != previous;
        }
        else {
            System.err.println("Invalid color settings");
            return false;
        }
    }

    boolean setSearchMode(String s) { //will have % as first char
        SearchMode previous = searchMode;
        if(s.equals("%term")) {
            searchMode = SearchMode.TERM;
        }
        else if(s.equals("%phrase")) {
            searchMode = SearchMode.PHRASE;
        }
        else if(s.equals("%fuzzy")) {
            searchMode = SearchMode.FUZZY;
        }
        else {
            throw new IllegalStateException("setSearchMode() should not be accessed with any other argument");
        }
        return searchMode != previous;
    }

    SearchExecutor updateConfig(SearchExecutor currentExecutor) {
        if(currentExecutor == null) {
            currentExecutor = new SearchExecutor();
        }
        currentExecutor.setLanguage(chosenLang);
        currentExecutor.resultLimit = searchLimit;
        currentExecutor.mode = searchMode;
        currentExecutor.context = details;
        currentExecutor.colorHighlighting = color;
        return currentExecutor;
    }


    private SearchBuilder() {}

    static SearchBuilder getBuilder() {
        if(builderSingleton == null) {
            builderSingleton = new SearchBuilder();
        }
        return builderSingleton;
    }

}
