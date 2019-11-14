package indexer;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.langdetect.OptimaizeLangDetector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

/*
make sure close()
english polish and generic analyzer, use getwrapper to choose
 */

public class TextIndexer {
    private Path indexPath;
    private IndexWriter writer;
    private IndexReader reader;
    IndexSearcher searcher;
    Mode mode = Mode.MONITOR;
    String dir = null;
    WatchService watcher;
    OptimaizeLangDetector langDetector;


    public enum Mode{
        PURGE, ADD, RM, REINDEX, LIST, MONITOR
    }

    //public static void main(String args[]) { }

    public TextIndexer() {
        try {
            indexPath = Paths.get(System.getProperty("user.home"), ".index");
            Files.createDirectories(indexPath);
        } catch (IOException e) {
            System.err.println("problem with IO " + e);
            throw new RuntimeException("problem with IO", e);
        }

        IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        conf.setRAMBufferSizeMB(256.0);
        conf.setCommitOnClose(true);
        try {
            //writer = new IndexWriter(new SimpleFSDirectory(indexPath), conf);
            writer = new IndexWriter(FSDirectory.open(indexPath), conf);
        } catch (IOException e) {
            System.err.println("directory cannot be read/written to or low level IO error");
            throw new RuntimeException("directory cannot be read/written to or low level IO error", e);
        }
    }

    //Cannot be add and remove
    public TextIndexer(Mode mode) {
        this();
        this.mode = mode;
    }

    //must be add or remove
    public TextIndexer(Mode mode, String dir) {
        this(mode);
        this.dir = dir;
    }

    public void startIndexer() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if(writer != null && writer.isOpen()) { //check is open
                    writer.close();
                }
                if(reader != null) { //check isopen nec
                    reader.close();
                }
                if(watcher != null) { //check is open nec
                    // https://stackoverflow.com/questions/42725861/how-to-terminate-continuous-thread-that-detects-new-file
                    // do this
                    watcher.close();
                }
            }catch (IOException e) {
                System.err.println("IOerror when closing down");
                throw new RuntimeException("IOerror when closing down", e);
            }
        }));

        switch (mode) {
            case PURGE:
                purgeIndex();
                break;
            case ADD:
                addDocsToIndex();
                break;
            case RM:
                removeDocsFromIndex();
                break;
            case REINDEX:
                reindex();
                break;
            case LIST:
                printDirectories();
                break;
            case MONITOR:
                monitorDirectories();
                break;
        }
        try {
            if(writer.isOpen()) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("low level IO problem when closing writer");
            throw new RuntimeException("low level IO problem when closing writer", e);
        }
    }

    private void purgeIndex() {
        try {
            writer.deleteAll();
        } catch (IOException e) {
            System.err.println("low level IO problem during purge");
            throw new RuntimeException("low level IO problem during purge", e);
        }

    }

    private void addDocsToIndex() { //method for idnexing then one for add idnex
        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                return visitFileToAdd(file);
            }
        };

        try {
            performFileTreeWalk(visitor);
        } catch (FileNotFoundException e) {
            System.err.println("Path " + dir + " does not lead to directory, nothing was indexed");
            try {
                writer.rollback();
            } catch (IOException e1) {
                System.err.println("unable to rollback");
            }
            throw new RuntimeException("Path " + dir + " does not lead to directory, nothing was indexed", e);
        }

        Path dirPath = Paths.get(dir);
        Document dirDoc = new Document();
        try {
            dirDoc.add(new StringField(FieldNames.DIRECTORY, dirPath.toFile().getCanonicalPath(), Field.Store.YES));
        } catch (IOException e) {
            System.err.println("Could not make necessary queries to form canocnial path for " + dirPath.toString());
            throw new RuntimeException("Could not make necessary queries to form canocnial path for " + dirPath.toString(), e);
        }
        try {
            writer.addDocument(dirDoc);
        } catch (IOException e) {
            System.err.println("Could not add directory info to index, aborting " + dirPath.toString());
            try {
                writer.rollback();
            } catch (IOException e1) {
                System.err.println("unable to rollback");
            }
            throw new RuntimeException("Could not add directory info to index, aborting " + dirPath.toString(), e);
        }
    }

    private void removeDocsFromIndex() {
        try  {
            reader = DirectoryReader.open(writer);
            Term directoryToRemove = new Term(FieldNames.DIRECTORY, new File(dir).getCanonicalPath());
            TermQuery dirQuery = new TermQuery(directoryToRemove);
            IndexSearcher searchDirectoriesIndexed = new IndexSearcher(reader);
            int numberOfDirectories = searchDirectoriesIndexed.count(dirQuery);
            if(numberOfDirectories == 0) {
                System.err.println("Directory " + dir + " was never indexed");
                throw new RuntimeException("Directory " + dir + " was never indexed");
            }
            if(numberOfDirectories > 1) {
                System.err.println("Directory " + dir + " was indexed more than once");
                throw new IllegalStateException("Directory " + dir + " was indexed more than once");
            }
            writer.deleteDocuments(directoryToRemove);
        } catch (IOException e) {
            System.err.println("Low level IO error when creating IndexReader");
            throw new RuntimeException(e);
        } finally {
            try {
                reader.close();
            }catch(IOException e) {
                System.err.println("unable to close indexreader");
                throw new RuntimeException("unable to close indexreader", e);
            }
        }

        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                return visitFileToRemove(file);
            }
        };

        try {
            performFileTreeWalk(visitor);
        } catch (FileNotFoundException e) {
            try {
                writer.rollback();
            } catch (IOException e1) {
                System.err.println("unable to rollback, index structure corrupted");
            }
            throw new IllegalStateException("File was checked to be a directory above", e);
        }
    }

    private void performFileTreeWalk (SimpleFileVisitor<Path> visitor) throws FileNotFoundException {
        if(dir != null) {
            Path dirToBeIndexed = Paths.get(dir);
            if (Files.isDirectory(dirToBeIndexed)) {
                try { //TODO remmove or modify this
                    Files.walkFileTree(dirToBeIndexed, visitor);
                } catch (IOException e) {
                    throw new IllegalStateException("Should have been caught inside visitor", e);
                }
            }
            else {
                throw new FileNotFoundException();
            }
        }
    }

    //TODO remembers last file, need to fix
    private FileVisitResult visitFileToAdd(Path file) {
        if (Files.isRegularFile(file)) {
            try {
                Document current = TextExtractor.getTextExtractor().parseFile(file);
                if(current != null) {
                    writer.addDocument(current);
                }
            } catch (FileNotFoundException e) {
                System.err.println("IllegalState");
                throw new IllegalStateException("Files.isRegularFile should have caught this", e);
            } catch (IOException e) {
                System.err.println("File " + file.getFileName() + " cannot be parsed");
            }
        } else {
            System.err.println("File " + file.getFileName() + " is not a regular file or doesn't exist");
        }
        return FileVisitResult.CONTINUE;
    }

    private FileVisitResult visitFileToRemove(Path file) {
        if (Files.isRegularFile(file)) {
            try (IndexReader read = DirectoryReader.open(writer)){
                writer.deleteDocuments(new Term(FieldNames.FILE_PATH, file.toFile().getCanonicalPath()));
            } catch (IOException e) {
                System.err.println("Unable to perform necessary filesystem queries for " + file.toString() + " skipping file");
            }
        } else {
            System.err.println("File " + file.getFileName() + " is not a regular file or doesn't exist, skipping");
        }
        return FileVisitResult.CONTINUE;
    }






    private void reindex() {
        String[] indexedDirectories = listDirectories();
        for(String directory : indexedDirectories) {
            dir = directory;
            removeDocsFromIndex();
            addDocsToIndex();
        }

    }

    private void printDirectories() {
        String[] directoriesToPrint = listDirectories();
        for(String directory : directoriesToPrint) {
            System.out.println(directory);
        }
    }

    private String[] listDirectories() {
        ArrayList<String> indexedDirNames = new ArrayList<>();
        try {
            reader = DirectoryReader.open(writer);
            Term directories = new Term(FieldNames.DIRECTORY, "*");
            WildcardQuery dirQuery = new WildcardQuery(directories);
            IndexSearcher searchDirectoriesIndexed = new IndexSearcher(reader);
            TotalHitCountCollector hits = new TotalHitCountCollector();
            searchDirectoriesIndexed.search(dirQuery, hits);
            int numberOfDirectories = hits.getTotalHits();
            TopDocs dirsFound;
            if (numberOfDirectories > 0) {
                dirsFound = searchDirectoriesIndexed.search(dirQuery, numberOfDirectories);
                for (int i = 0; i < numberOfDirectories; i++) {
                    Document current = reader.document(dirsFound.scoreDocs[i].doc);
                    indexedDirNames.add(current.get(FieldNames.DIRECTORY));
                }
            }

            } catch(IOException e){
                System.err.println("Low level IO error when creating IndexWriter");
                throw new RuntimeException(e);
            } finally{
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("unable to close indexreader");
                    throw new RuntimeException("unable to close indexreader", e);
                }
            }
            return indexedDirNames.toArray(new String[indexedDirNames.size()]);

    }

    private void monitorDirectories() {
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            System.err.println("Cannot instantiate watchservice, IO error");
            throw new RuntimeException("Cannot instantiate watchservice, IO error", e);
        }
        String[] directoriesToMonitor = listDirectories();
        WatchKey key;
        Path directoryPath;
        for(String directoryToMonitor : directoriesToMonitor) {
            directoryPath = Paths.get(directoryToMonitor);
            try {
                directoryPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            } catch (IOException e) {
                System.err.println("registering directory for Watch service resulted in IOException");
                throw new RuntimeException("registering directory for Watch service resulted in IOException", e);
            }
        }
        while(true) {
            try {
                key = watcher.take();
                System.out.println("Change detected");
                handleWatchServiceEvent(key);
            } catch (InterruptedException e) {
                System.err.println("Watcher interrupted");
            }
        }
    }


    private void handleWatchServiceEvent(WatchKey key) {
        System.out.println("event detected");
        //Can't be anything else
        Path dirPath = (Path) key.watchable();
        List<WatchEvent<?>> events = key.pollEvents();
        Path relative;
        Path combined;
        for(WatchEvent event : events) {
            relative = (Path) event.context();
            combined = dirPath.resolve(relative);

            if(event.kind() == ENTRY_CREATE) {
                visitFileToAdd(combined);
            }
            else if(event.kind() == ENTRY_DELETE) {
                try {
                    writer.deleteDocuments(new Term(FieldNames.FILE_PATH, combined.toFile().getCanonicalPath()));
                } catch (IOException e) {
                    System.err.println("Low level IO error when deleting file " + combined);
                    throw new RuntimeException("Low level IO error when deleting file " + combined, e);
                }

            }
            else if(event.kind() == ENTRY_MODIFY) {
                visitFileToRemove(combined);
                visitFileToAdd(combined);
            }
            else {
                System.err.println("OVERFLOW");
                throw new RuntimeException("OVERFLOW");
            }
        }
    }
}
