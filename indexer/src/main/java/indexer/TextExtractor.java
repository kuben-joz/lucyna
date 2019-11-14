package indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.tika.Tika;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/*
TODO add standard analyzer upon idnexing
getreouse as stream only works for class dir

 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

public class TextExtractor {

    //source: https://www.freeformatter.com/mime-types-list.html
    /*
    All of the below file types are detected by default detector
    private static final String TXT_NAME = "text/plain";
    private static final String PDF_NAME = "application/pdf";
    private static final String RTF1_NAME = "application/rtf";
    private static final String RTF2_NAME = "text/richtext";
    private static final String ODT_NAME = "application/vnd.oasis.opendocument.text";
    private static final String DOCX_NAME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    */

    private Tika tika = new Tika();
    private AutoDetectParser detector = new AutoDetectParser(new DefaultDetector());
    private static TextExtractor extractorSingleton = null;
    private OptimaizeLangDetector langDetector;
    //Possibly better as includes the title tag
    //ContentHandler handler = new ToXMLContentHandler();

    private TextExtractor() { }

    public static TextExtractor getTextExtractor() {
        if(extractorSingleton == null) {
            extractorSingleton = new TextExtractor();
            HashSet<String> languages = new HashSet<>();
            languages.add("en");
            languages.add("pl");
            extractorSingleton.langDetector = new OptimaizeLangDetector();
            try {
                extractorSingleton.langDetector.loadModels(languages);
            } catch (IOException e) {
                System.err.println("unable to load language models, IO error" + e);
                throw new RuntimeException("unable to load language models, IO error" + e);
            }
        }
        return extractorSingleton;

    }

    //takes Path and returns prepared lucene Document, null if failed???
    // coudl throw ifle nto found exception
    public Document parseFile(Path path) {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();


        try {
            InputStream fileAsStream = Files.newInputStream(path);
            detector.parse(fileAsStream, handler, metadata);
        } catch (IOException e) {
            System.err.println("Document " + path + "stream could not be read, skipping file");
            return null;
        } catch (SAXException e) {
            System.err.println("Document " + path + "SAX events could not be processed, skipping file");
            return null;
        } catch (TikaException e) {
            System.err.println("Document " + path + "couldn't be parsed, skipping file");
            return null;
        }

        Document doc = new Document();
        //doc.add(getNewBodyField(handler.toString()));
        try {
            doc.add(getNewBodyField(path));
        } catch (IOException e) {
            e.printStackTrace();
            //TODO
        }

        try {
            doc.add(new StringField(FieldNames.FILE_PATH, path.toFile().getCanonicalPath(), Field.Store.YES));
        } catch (IOException e) {
            System.err.println("Unable to perform necessary filesystem queries fr " + path.toString() + " skipping file");
            return null;
        }
        String body = handler.toString();
        langDetector.addText(body.toCharArray(), 0, body.length());
        if(langDetector.hasEnoughText()) {
            List<LanguageResult> languages = langDetector.detectAll();
            doc.add(new StringField(FieldNames.LANGUAGES, languages.get(0).getLanguage(), Field.Store.NO)); //can search still, field sotr eno doesnt displa contetns but does index it
        }
        else {
            doc.add(new StringField(FieldNames.LANGUAGES, "en", Field.Store.NO));
            doc.add(new StringField(FieldNames.LANGUAGES, "pl", Field.Store.NO));
            System.err.println("Couldn't detect doc language, marked as both pl and en");
        }
        if(metadata.get(TikaCoreProperties.TITLE) != null) {
            doc.add(getNewTitleField(metadata.get(TikaCoreProperties.TITLE)));
        }
        return doc;
    }

    private Field getNewBodyField(String content) {
        FieldType ft = new FieldType();
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);

        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPositions(true);
        ft.setTokenized(true);
        ft.setStored(true);
        ft.freeze();
        return new StoredField(FieldNames.BODY, content, ft);
        //return new TextField(FieldNames.BODY, content, Field.Store.YES);
    }

    private IndexableField getNewBodyField(Path path) throws IOException{
       /* FieldType ft = new FieldType();
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);

        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPositions(true);
        ft.setTokenized(true);
        ft.setStored(true);
        ft.freeze();
        return new StoredField(FieldNames.BODY, content, ft); */
       TextField ft = new TextField(FieldNames.BODY, new String(Files.readAllBytes(path)), Field.Store.YES);
        return ft;
    }

    private Field getNewTitleField(String content) {
        FieldType ft = new FieldType();
        ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        ft.setStoreTermVectors(true);
        ft.setStoreTermVectorOffsets(true);
        ft.setStoreTermVectorPositions(true);
        ft.setTokenized(true);
        ft.setStored(true);
        ft.freeze();
        return new StoredField(FieldNames.TITLE, content, ft);

    }


}
