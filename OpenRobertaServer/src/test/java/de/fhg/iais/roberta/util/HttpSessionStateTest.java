package de.fhg.iais.roberta.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import de.fhg.iais.roberta.factory.RobotFactory;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.util.dbc.Assert;
import joptsimple.OptionParser;
import joptsimple.OptionSpec;

public class HttpSessionStateTest {

    HttpSessionState httpSessionState;

    @Before
    public void setup() {
        OptionParser parser = new OptionParser();
        OptionSpec<String> serverDefineOpt = parser.acceptsAll(Arrays.asList("d", "server-property")).withRequiredArg().ofType(String.class);
        List<String> serverDefines = serverDefineOpt.options();

        Map<String, RobotFactory> robotPluginMap = new HashMap<>();
        for ( String board : Arrays.asList("uno", "nano", "mega") ) {
            robotPluginMap.put(board, new RobotFactory(new PluginProperties(board, "", "", Util.loadPropertiesRecursively("classpath:/" + board + ".properties"))));
        }
        ServerProperties serverProperties = new ServerProperties(Util.loadAndMergeOpenRobertaServerProperties("classpath:/openRoberta.properties", serverDefines));

        this.httpSessionState = HttpSessionState.init(robotPluginMap, serverProperties, "en", "");
    }

    @Test
    public void getRobotFactoriesOfGroup_ShouldReturnMembers_WhenGivenValidGroup() {
        List<RobotFactory> arduinoMembers = this.httpSessionState.getRobotFactoriesOfGroup("arduino");
        Assert.isTrue(arduinoMembers.size() == 3);
        Assert.isTrue(arduinoMembers.stream().allMatch(factory -> factory.getGroup().equals("arduino")));
    }

    @Test
    public void getRobotFactoriesOfGroup_ShouldReturnEmptyList_WhenGivenInvalidGroup() {
        List<RobotFactory> imaginaryTestGroupMembers = this.httpSessionState.getRobotFactoriesOfGroup("imaginaryTestGroup");
        Assert.isTrue(imaginaryTestGroupMembers.isEmpty());
    }
}
