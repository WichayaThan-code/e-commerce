package com.thaishopfun.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonMapsTest {
    @Test
    void readsOptionSchemaAfterPostgresNormalizesWhitespace() {
        var groups = JsonMaps.readGroups("[{\"name\": \"ไซซ์\", \"values\": [\"S\", \"M\", \"L\"]}]");
        assertEquals(1, groups.size());
        assertEquals("ไซซ์", groups.get(0).name());
        assertEquals(List.of("S", "M", "L"), groups.get(0).values());
    }

    @Test
    void optionSchemaAndSkuValuesRoundTripWithSpaces() {
        var writtenGroups = JsonMaps.writeGroups(List.of(new CatalogDtos.OptionGroup("ไซซ์", List.of("S", "M", "L"))));
        var spacedGroups = writtenGroups.replace(":", ": ").replace(",", ", ");
        var groups = JsonMaps.readGroups(spacedGroups);
        assertEquals("ไซซ์", groups.get(0).name());
        assertEquals(List.of("S", "M", "L"), groups.get(0).values());

        var writtenValues = JsonMaps.write(Map.of("สี", "แดง", "ไซซ์", "M"));
        var spacedValues = writtenValues.replace(":", ": ").replace(",", ", ");
        var values = JsonMaps.read(spacedValues);
        assertEquals("แดง", values.get("สี"));
        assertEquals("M", values.get("ไซซ์"));
    }
}
