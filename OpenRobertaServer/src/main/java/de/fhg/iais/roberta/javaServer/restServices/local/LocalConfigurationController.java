package de.fhg.iais.roberta.javaServer.restServices.local;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.generated.restEntities.BaseResponse;
import de.fhg.iais.roberta.generated.restEntities.ConfRequest;
import de.fhg.iais.roberta.generated.restEntities.ConfResponse;
import de.fhg.iais.roberta.generated.restEntities.FullRestRequest;
import de.fhg.iais.roberta.generated.restEntities.SaveConfRequest;
import de.fhg.iais.roberta.javaServer.restServices.local.LocalProgramStore.Entry;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.UtilForREST;

/**
 * The named robot configurations of BlockCore: the REST interface of the Open Roberta Lab ("/conf/..."), stored as files (see
 * {@link LocalProgramStore}).
 */
@Path("/conf")
public class LocalConfigurationController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalConfigurationController.class);

    /** saveC: save (an existing configuration may be overwritten), saveAsC: save under a new name */
    @POST
    @Path("/saveC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveConfig(FullRestRequest fullRequest) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, fullRequest, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            SaveConfRequest request = SaveConfRequest.make(fullRequest.getData());
            String name = request.getName();
            BaseResponse response = BaseResponse.make();
            response.setCmd(request.getCmd());
            LocalProgramStore store = LocalProgramStore.get();
            if ( !LocalProgramStore.isValidName(name) ) {
                UtilForREST.addErrorInfo(response, Key.CONFIGURATION_ERROR_ID_INVALID);
            } else if ( !"saveC".equals(request.getCmd()) && store.configurationExists(name) ) {
                UtilForREST.addErrorInfo(response, Key.CONFIGURATION_SAVE_AS_ERROR_CONFIGURATION_EXISTS);
            } else {
                store.saveConfiguration(name, request.getConfiguration());
                UtilForREST.addSuccessInfo(response, Key.CONFIGURATION_SAVE_SUCCESS);
            }
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("saving a configuration failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/loadC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loadConfig(FullRestRequest fullRequest) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, fullRequest, true);
        try {
            ConfRequest request = ConfRequest.make(fullRequest.getData());
            ConfResponse response = ConfResponse.make();
            response.setCmd("loadC");
            String configuration = LocalProgramStore.get().loadConfiguration(request.getName());
            if ( configuration == null ) {
                UtilForREST.addErrorInfo(response, LocalProgramStore.isValidName(request.getName()) ? Key.CONFIGURATION_GET_ONE_ERROR_NOT_FOUND : Key.CONFIGURATION_ERROR_ID_INVALID);
            } else {
                response.setConfXML(configuration);
                UtilForREST.addSuccessInfo(response, Key.CONFIGURATION_GET_ONE_SUCCESS);
            }
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("loading a configuration failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/deleteC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteConfig(FullRestRequest fullRequest) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, fullRequest, true);
        try {
            ConfRequest request = ConfRequest.make(fullRequest.getData());
            BaseResponse response = BaseResponse.make();
            response.setCmd("deleteC");
            if ( LocalProgramStore.get().deleteConfiguration(request.getName()) ) {
                UtilForREST.addSuccessInfo(response, Key.CONFIGURATION_DELETE_SUCCESS);
            } else {
                UtilForREST.addErrorInfo(response, Key.CONFIGURATION_DELETE_ERROR);
            }
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("deleting a configuration failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    /** [name, owner, created, last changed] per configuration */
    @POST
    @Path("/loadCN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loadConfigNames(FullRestRequest fullRequest) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, fullRequest, true);
        try {
            ConfResponse response = ConfResponse.make();
            response.setCmd("loadCN");
            JSONArray infos = new JSONArray();
            for ( Entry configuration : LocalProgramStore.get().listConfigurations() ) {
                infos.put(new JSONArray().put(configuration.name).put(LocalUserController.ACCOUNT).put(configuration.created).put(configuration.lastChanged));
            }
            response.setConfigurationNames(infos);
            UtilForREST.addSuccessInfo(response, Key.CONFIGURATION_GET_ALL_SUCCESS);
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("listing the configurations failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }
}
