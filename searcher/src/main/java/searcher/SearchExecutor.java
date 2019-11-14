package searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

/* TODO
FINISH EXCEPTION FOR GETQUERY
add variables to poms
change analyzer to stempel
take the search mode enum out
use the writer read from other package maybe?

 */


class SearchExecutor {
    IndexSearcher searcher;
    TermQuery language = new TermQuery(new Term(FieldNames.LANGUAGES, "en"));
    int resultLimit = Integer.MAX_VALUE;
    SearchBuilder.SearchMode mode = SearchBuilder.SearchMode.TERM;
    Analyzer analyse = new StandardAnalyzer(); //change to stempel for pl
    boolean context = false;
    boolean colorHighlighting = false;
    UnifiedHighlighter highlighter;

    SearchExecutor() {
        Path indexPath = Paths.get(System.getProperty("user.home"), ".index");
        Directory indexDir;
        IndexReader reader;
        try {
            indexDir = FSDirectory.open(indexPath);
            reader = DirectoryReader.open(indexDir);
        } catch (IOException e) {
            System.err.println("IO error when creating streams, aborting");
            throw new RuntimeException("IO error when creating streams, aborting", e);
        }
        searcher = new IndexSearcher(reader);
        highlighter = new UnifiedHighlighter(searcher, analyse);
    }


    void search(String queryText, Terminal terminal) {
        //String[] fieldNames = {FieldNames.TITLE, FieldNames.BODY};
        String[] fieldNames = {FieldNames.BODY};


        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.setMinimumNumberShouldMatch(1); //at least one hit except language type
        queryBuilder.add(language, BooleanClause.Occur.MUST);
        Query queryOfText = null;
        try {
            queryOfText = getQuery(queryText, fieldNames, queryBuilder);
        } catch (IOException e) {
            return;
            //TODO
        } catch (IllegalQueryException e) {
            System.err.println("Query: " + e.query +
                                "cannot be used to perform a search," +
                                " please try to be more specific"); //because it removes queries like ??
            return;
        }
        assert queryOfText != null;
        queryBuilder.add(queryOfText, BooleanClause.Occur.SHOULD); //change to must so dont get empty strings, especially if dont search title
        Query finalQuery = queryBuilder.build();
        System.out.println("FINALQUERY");
        System.out.println(finalQuery);
        System.out.println("FINALQUERY");
        TopDocs results;
        try {
            results = searcher.search(finalQuery, resultLimit);
        } catch (IOException e) {
            System.err.println("IOError performing search");
            return;
        }
        printResults(finalQuery, results, queryOfText, terminal);
    }

    private Query getQuery(String queryText, String[] fieldnames, BooleanQuery.Builder queryBuilder) throws IOException, IllegalQueryException{
        try(TokenStream queryBodyStream = analyse.tokenStream(FieldNames.BODY, queryText)){ //Fieldname not used by our analyzers

            CharTermAttribute textChar = queryBodyStream.addAttribute(CharTermAttribute.class);
            ArrayList<String> termsCombined = new ArrayList<>();
            queryBodyStream.reset();
            while(queryBodyStream.incrementToken()) {
                termsCombined.add(textChar.toString());
            }
            if(termsCombined.size() == 0) {
                throw new IllegalQueryException(queryText);
            }
            //for(String fieldname : fieldnames) {
            //Query temp = createQueryOfCorrectType(termsCombined.toArray(new String[termsCombined.size()]), fieldname); //Empty array more thread safe
            String[] queryString = termsCombined.toArray(new String[termsCombined.size()]);
            System.out.println("QUERY");
            System.out.println(queryString);
            System.out.println("QUERY");
            Query temp = createQueryOfCorrectType(termsCombined, FieldNames.BODY);
            //}
            return temp;
        }catch (IOException e) {
            System.err.println("IOError when using tokenstreams to generate query, aborting");
            throw e;
        }

    }

    private Query createQueryOfCorrectType(ArrayList<String> words, String fieldname) {
        switch(mode) {
            case TERM:
                return getTermQuery(words.get(0), fieldname); //might be able to cut text earlier
            case PHRASE:
                return getPhraseQuery(words, fieldname);
            case FUZZY:
                return getFuzzyQuery(words.get(0), fieldname);
            default:
                throw new IllegalStateException();
        }
    }

    private TermQuery getTermQuery(String termText, String fieldName) {
        TermQuery fieldQ = new TermQuery(new Term(fieldName, termText));
        return fieldQ;
        /*BooleanQuery.Builder resultBuilder = new BooleanQuery.Builder();
        resultBuilder.add(fieldQ, BooleanClause.Occur.SHOULD);
        return resultBuilder.build();*/
    }

    private PhraseQuery getPhraseQuery(ArrayList<String> termText, String fieldName) {
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for(String word : termText) {
            builder.add(new Term(fieldName, word));
        }
        return builder.build();
        /*BooleanQuery.Builder resultBuilder = new BooleanQuery.Builder();
        resultBuilder.add(builder.build(), BooleanClause.Occur.SHOULD);
        return resultBuilder.build();*/
    }

    private FuzzyQuery getFuzzyQuery(String termText, String fieldname) {
        FuzzyQuery fieldQ = new FuzzyQuery(new Term(fieldname, termText), FuzzyQuery.defaultMaxEdits, FuzzyQuery.defaultPrefixLength, FuzzyQuery.defaultMaxExpansions, false);
        return fieldQ;
        /*BooleanQuery.Builder resultBuilder = new BooleanQuery.Builder();
        resultBuilder.add(fieldQ, BooleanClause.Occur.SHOULD);
        return resultBuilder.build();*/
    }


    private void printResults(Query query, TopDocs results, Query textQuery, Terminal terminal) {
        terminal.writer().println("File count: " + results.totalHits);
        String[] fragments = null;
        String[] ANSIFragments = null;
        if(context) {
            UnifiedHighlighter highlighter = new UnifiedHighlighter(searcher, analyse);
            try {
                QueryParser b = new QueryParser(FieldNames.BODY, analyse);
                //TODO change to interger maxvalue
                fragments = highlighter.highlight(FieldNames.BODY, textQuery, results, 10);
            } catch (IOException e) {
                e.printStackTrace();
                //TODO
            }
            ANSIFragments = new String[fragments.length];
            for(int i = 0; i < fragments.length; i++) {
                String[] temp = fragments[i].split("<b>|</b>");
                AttributedStringBuilder SBuild = new AttributedStringBuilder();
                for(int j = 0; j < temp.length/2; j++) {
                    SBuild.append(temp[j*2]).style(AttributedStyle.BOLD);
                    if(colorHighlighting) {
                        SBuild.ansiAppend("\033[31m");
                    }
                            //SBuild.append(temp[j*2 + 1]).style(AttributedStyle.BOLD_OFF).;
                    SBuild.append(temp[j*2 + 1]).style(AttributedStyle.DEFAULT);
                }
                ANSIFragments[i] = SBuild.toAnsi(terminal);
            }
            if(colorHighlighting) {
                //TODO
            }
        }
        for(int i = 0; i < results.scoreDocs.length; i++) {
            //totalHits.value is actually a long, so I'm using results.scoreDocs.length
            int docIndexVal = results.scoreDocs[i].doc;
            try {
                Document doc = searcher.doc(docIndexVal);
                //System.out.println(doc.get(FieldNames.FILE_PATH));
                terminal.writer().println(doc.get(FieldNames.FILE_PATH));
            } catch (IOException e) {
                System.err.println("IO error when accessing document with id " + docIndexVal + "cant print result");
            }
            if(context) {
                assert ANSIFragments != null;
                //System.out.println(fragments[i]);
                terminal.writer().println(ANSIFragments[i]);
            }
        }

    }



    //transpositons false

    public void reset() {

    }

    void setLanguage(SearchBuilder.Language lang) {
        if(lang == SearchBuilder.Language.EN) {
            language = new TermQuery(new Term(FieldNames.LANGUAGES, "en"));
        }
        else {
            language = new TermQuery(new Term(FieldNames.LANGUAGES, "pl"));
        }
    }


}
