package bacta.tre;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by crush on 3/19/14.
 * <p>
 * <code>
 * public class Header {
 * int token;
 * int version;
 * int numberOfFiles;
 * int tocOffset;
 * int tocCompressor;
 * int sizeOfTOC;
 * int blockCompressor;
 * int sizeOfNameBlock;
 * int uncompSizeOfNameBlock;
 * }
 * <p>
 * public class TableOfContentsEntry {
 * int crc;
 * int length;
 * int offset;
 * int compressor;
 * int compressedLength;
 * int fileNameOffset;
 * }
 * </code>
 */
class SearchTree extends SearchNode {
    private static final int RECORD_SIZE = 24;

    private final String filePath;

    public final String getFilePath() {
        return filePath;
    }

    private Map<String, TreeRecordInfo> archivedFiles = new HashMap<>();

    private int recordsOffset;
    private int recordsCompressionLevel;
    private int recordsDeflatedSize;
    private int namesCompressionLevel;
    private int namesDeflatedSize;
    private int namesInflatedSize;

    public SearchTree(String filePath, int priority) {
        super(priority);

        this.filePath = filePath;
    }

    public final void preprocess() throws
            FileNotFoundException,
            IOException,
            UnsupportedTreeFileException,
            UnsupportedTreeFileVersionException {

        logger.trace(filePath);

        final RandomAccessFile file = new RandomAccessFile(filePath, "r");
        final FileChannel channel = file.getChannel();
        final MappedByteBuffer buffer = (MappedByteBuffer) channel.map(
                FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN);

        channel.close();
        file.close();

        if (buffer.remaining() < 4)
            throw new UnsupportedTreeFileException(1);

        //Read the file.
        final int fileId = buffer.getInt();

        if (fileId != TreeFile.ID_TREE)
            throw new UnsupportedTreeFileException(fileId);

        final int version = buffer.getInt();

        if (version != TreeFile.ID_0005 && version != TreeFile.ID_0006)
            throw new UnsupportedTreeFileVersionException(version);

        final int totalRecords = buffer.getInt();

        archivedFiles = new HashMap<>(totalRecords);

        recordsOffset = buffer.getInt();
        recordsCompressionLevel = buffer.getInt();
        recordsDeflatedSize = buffer.getInt();
        namesCompressionLevel = buffer.getInt();
        namesDeflatedSize = buffer.getInt();
        namesInflatedSize = buffer.getInt();

        buffer.position(recordsOffset);

        final ByteBuffer recordData = ByteBuffer.allocate(RECORD_SIZE * totalRecords);
        final ByteBuffer namesData = ByteBuffer.allocate(namesInflatedSize);

        TreeFile.inflate(buffer, recordData, recordsCompressionLevel, recordsDeflatedSize);
        TreeFile.inflate(buffer, namesData, namesCompressionLevel, namesDeflatedSize);

        final ByteBuffer checksumData = buffer.slice();

        for (int i = 0; i < totalRecords; ++i) {
            ByteBuffer data = recordData.order(ByteOrder.LITTLE_ENDIAN);

            final TreeRecordInfo record = new TreeRecordInfo();
            record.checksum = data.getInt();
            record.inflatedSize = data.getInt();
            record.dataOffset = data.getInt();
            record.compressionLevel = data.getInt();
            record.deflatedSize = data.getInt();
            record.nameOffset = data.getInt();

            if (record.compressionLevel == 0)
                record.deflatedSize = record.inflatedSize;

            //Find the end of the string.
            final StringBuilder stringBuilder = new StringBuilder();
            namesData.position(record.nameOffset);

            byte b = 0;
            while ((b = namesData.get()) != 0)
                stringBuilder.append((char) b);

            record.filePath = stringBuilder.toString();
            checksumData.get(record.md5);

            archivedFiles.put(record.filePath, record);
        }
    }

    @Override
    public byte[] open(String filePath) {
        final TreeRecordInfo record = archivedFiles.get(filePath);
        byte[] bytes = null;

        if (record != null) {
            ByteBuffer buffer = null;

            try {
                RandomAccessFile file = new RandomAccessFile(this.filePath, "r");
                FileChannel channel = file.getChannel();
                MappedByteBuffer fileBuffer = channel.map(FileChannel.MapMode.READ_ONLY, record.dataOffset, record.deflatedSize);

                buffer = ByteBuffer.allocate(record.inflatedSize);

                TreeFile.inflate(fileBuffer, buffer, record.compressionLevel, record.deflatedSize);

                bytes = buffer.array();

                channel.close();
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }

    @Override
    public boolean exists(String filePath) {
        return archivedFiles.containsKey(filePath);
    }

    public Set<String> listFiles() {
        return archivedFiles.keySet();
    }

    private final class TreeRecordInfo {
        public String filePath;
        public int checksum;
        public int compressionLevel;
        public int deflatedSize;
        public int inflatedSize;
        public int nameOffset;
        public int dataOffset;
        public byte[] md5 = new byte[16];
    }
}