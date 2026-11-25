package de.fhg.iais.roberta.javaServer.restServices.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalUpdateTest {
    @Test
    public void parsesStableAndPreReleaseVersions() {
        assertEquals("1.0.15", join(LocalUpdate.parse("v1.0.15")));
        assertEquals("1.1.20-beta", join(LocalUpdate.parse("v1.1.20-beta")));
        assertNull(LocalUpdate.parse("windows-14"));
        assertNull(LocalUpdate.parse("1.0-dev"));
    }

    @Test
    public void comparesNumbersFirstAndPrefersStable() {
        assertTrue(LocalUpdate.compare(LocalUpdate.parse("1.1.20-beta"), LocalUpdate.parse("1.0.19")) > 0);
        assertTrue(LocalUpdate.compare(LocalUpdate.parse("1.0.19"), LocalUpdate.parse("1.0.9")) > 0);
        assertTrue(LocalUpdate.compare(LocalUpdate.parse("1.1.20"), LocalUpdate.parse("1.1.20-beta")) > 0);
        assertEquals(0, LocalUpdate.compare(LocalUpdate.parse("1.1.20-beta"), LocalUpdate.parse("v1.1.20-beta")));
    }

    private static String join(Object[] v) {
        return v[0] + "." + v[1] + "." + v[2] + (((String) v[3]).isEmpty() ? "" : "-" + v[3]);
    }
}
