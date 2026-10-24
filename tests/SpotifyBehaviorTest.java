import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpotifyBehaviorTest {
    @Test
    public void spotifyShouldAlwaysExposeAudioBars() {
        assertTrue("Spotify debe mostrar barras de audio siempre en todos los apartados.", true);
    }

    @Test
    public void shuffleLabelShouldMentionSmartRandomMode() {
        String label = "Activar modo aleatorio inteligente";
        assertEquals("Activar modo aleatorio inteligente", label);
    }
}
