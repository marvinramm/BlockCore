package de.fhg.iais.roberta.syntax.configuration.sensor;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.transformer.forClass.NepoConfiguration;
import de.fhg.iais.roberta.util.dbc.DbcException;

@NepoConfiguration(name = "TCS34725", category = "CONFIGURATION_SENSOR", blocklyNames = {"robConf_tcs34725"})
public final class Tcs34725Sensor extends ConfigurationComponent {
    private Tcs34725Sensor() {
        super(null, null, null, null, null);
        throw new DbcException("should NEVER be called");
    }
}
