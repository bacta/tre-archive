package bacta.tre;

/**
 * Created by crush on 3/19/14.
 */
class SearchTOC extends SearchNode {

    public class Header {
        int token;
        int version;
        byte tocCompressor;
        byte fileNameBlockCompressor;
        byte unusedOne;
        byte unusedTwo;
        int numberOfFiles;
        int sizeOfTOC;
        int sizeOfNameBlock;
        int uncompSizeOfNameBlock;
        int numberOfTreeFiles;
        int sizeOfTreeFileNameBlock;
    }

    public class TableOfContentsEntry {
        byte compressor;
        byte unused;
        short treeFileIndex;
        int crc;
        int fileNameOffset;
        int offset;
        int length;
        int compressedLength;
    }

    private final String filePath;

    public final String getFilePath() {
        return filePath;
    }

    public SearchTOC(String filePath, int searchPriority) {
        super(searchPriority);

        this.filePath = filePath;
    }

    @Override
    public byte[] open(String filePath) {
        return null;
    }

    @Override
    public boolean exists(String filePath) {
        return false;
    }
}