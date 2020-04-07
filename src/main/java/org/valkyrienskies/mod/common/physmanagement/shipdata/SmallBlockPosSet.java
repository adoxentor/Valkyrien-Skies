package org.valkyrienskies.mod.common.physmanagement.shipdata;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.mod.common.physmanagement.shipdata.SmallBlockPosSet.SmallBlockPosSetDeserializer;
import org.valkyrienskies.mod.common.physmanagement.shipdata.SmallBlockPosSet.SmallBlockPosSetSerializer;
import org.valkyrienskies.mod.common.util.VSIterationUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;

/**
 * An implementation of IBlockPosSet that stores block positions as 1 integer. This is accomplished by storing each
 * position as its relative coordinates to the centerX and centerZ values of this set. In this implementation the x
 * and z positions are 12 bits each, so they can range anywhere from -2048 to + 2047 relative to centerX and centerZ.
 * This leaves 8 bits for storing the y coordinate, which allows it the range of 0 to 255, exactly the same as
 * Minecraft.
 */
@JsonDeserialize(using = SmallBlockPosSetDeserializer.class)
@JsonSerialize(using = SmallBlockPosSetSerializer.class)
public class SmallBlockPosSet implements IBlockPosSet {

    private static final int BOT_12_BITS = 0x00000FFF;
    private static final int BOT_8_BITS = 0x000000FF;

    @Nonnull
    private final TIntSet compressedBlockPosSet;
    @Getter
    private final int centerX, centerZ;

    public SmallBlockPosSet(int centerX, int centerZ) {
        this.compressedBlockPosSet = new TIntHashSet();
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    @Override
    public boolean add(int x, int y, int z) throws IllegalArgumentException {
        if (!canStore(x, y, z)) {
            throw new IllegalArgumentException("Cannot store block position at <" + x + "," + y + "," + z + ">");
        }
        return compressedBlockPosSet.add(compress(x, y, z));
    }

    @Override
    public boolean remove(int x, int y, int z) {
        if (!canStore(x, y, z)) {
            // Nothing to remove
            return false;
        }
        return compressedBlockPosSet.remove(compress(x, y, z));
    }

    @Override
    public boolean contains(int x, int y, int z) {
        if (!canStore(x, y, z)) {
            // This pos cannot exist in this set
            return false;
        }
        return compressedBlockPosSet.contains(compress(x, y, z));
    }

    @Override
    public boolean canStore(int x, int y, int z) {
        int xLocal = x - centerX;
        int zLocal = z - centerZ;
        return !(y < 0 | y > 255 | xLocal < -2048 | xLocal > 2047 | zLocal < -2048 | zLocal > 2047);
    }

    @Override
    public int size() {
        return compressedBlockPosSet.size();
    }

    @Nonnull
    @Override
    public Iterator<BlockPos> iterator() {
        return new SmallBlockPosIterator(compressedBlockPosSet.iterator());
    }

    @Override
    public void forEach(@Nonnull VSIterationUtils.IntTernaryConsumer action) {
        TIntIterator iterator = compressedBlockPosSet.iterator();
        while (iterator.hasNext()) {
            int compressed = iterator.next();
            // Repeated code from decompress() because java has no output parameters.
            int z = compressed >> 20;
            int y = (compressed >> 12) & BOT_8_BITS;
            // this basically left-pads the int when casting so that the sign is preserved
            // not sure if there is a better way
            int x = (compressed & BOT_12_BITS) << 20 >> 20;
            action.accept(x + centerX, y, z + centerZ);
        }
    }

    @Override
    public void clear() {
        compressedBlockPosSet.clear();
    }

    @Nonnull
    private BlockPos decompress(int compressed) {
        int z = compressed >> 20;
        int y = (compressed >> 12) & BOT_8_BITS;
        // this basically left-pads the int when casting so that the sign is preserved
        // not sure if there is a better way
        int x = (compressed & BOT_12_BITS) << 20 >> 20;
        return new BlockPos(x + centerX, y, z + centerZ);
    }

    private int compress(int x, int y, int z) {
        // Allocate 12 bits for x, 12 bits for z, and 8 bits for y.
        int xBits = (x - centerX) & BOT_12_BITS;
        int yBits = y & BOT_8_BITS;
        int zBits = (z - centerZ) & BOT_12_BITS;
        return xBits | (yBits << 12) | (zBits << 20);
    }

    private class SmallBlockPosIterator implements Iterator<BlockPos> {

        private final TIntIterator iterator;

        SmallBlockPosIterator(TIntIterator intIterator) {
            this.iterator = intIterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public BlockPos next() {
            return decompress(iterator.next());
        }

    }

    public static class SmallBlockPosSetSerializer extends StdSerializer<SmallBlockPosSet> {

        public SmallBlockPosSetSerializer() {
            super((Class<SmallBlockPosSet>) null);
        }

        @Override
        public void serialize(SmallBlockPosSet value, JsonGenerator gen,
            SerializerProvider provider) throws IOException {

            gen.writeStartObject();

            gen.writeFieldName("positions");
            gen.writeStartArray(value.compressedBlockPosSet.size());
            TIntIterator iter = value.compressedBlockPosSet.iterator();

            while (iter.hasNext()) {
                gen.writeNumber(iter.next());
            }
            gen.writeEndArray();

            gen.writeNumberField("centerX", value.centerX);
            gen.writeNumberField("centerZ", value.centerZ);

            gen.writeEndObject();
        }

    }

    public static class SmallBlockPosSetDeserializer extends StdDeserializer<SmallBlockPosSet> {

        public SmallBlockPosSetDeserializer() {
            super((Class<?>) null);
        }

        @Override
        public SmallBlockPosSet deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
            JsonNode node = p.getCodec().readTree(p);

            int centerX = node.get("centerX").asInt();
            int centerZ = node.get("centerZ").asInt();

            SmallBlockPosSet set = new SmallBlockPosSet(centerX, centerZ);

            node.get("positions").forEach(elem -> {
                set.compressedBlockPosSet.add(elem.asInt());
            });

            return set;
        }
    }
}
