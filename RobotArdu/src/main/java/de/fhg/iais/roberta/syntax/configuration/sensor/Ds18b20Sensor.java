package de.fhg.iais.roberta.syntax.configuration.sensor;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.transformer.forClass.NepoConfiguration;
import de.fhg.iais.roberta.util.dbc.DbcException;

/** DS18B20 temperature sensor (1-Wire). */
@NepoConfiguration(name = "DS18B20", category = "CONFIGURATION_SENSOR", blocklyNames = {"robConf_ds18b20"})
public final class Ds18b20Sensor extends ConfigurationComponent {
    private Ds18b20Sensor() {
        super(null, null, null, null, null);
        throw new DbcException("should NEVER be called");
    }
}
