package de.fhg.iais.roberta.javaServer.restServices.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.blockly.generated.BlockSet;
import de.fhg.iais.roberta.blockly.generated.Export;
import de.fhg.iais.roberta.factory.RobotFactory;
import de.fhg.iais.roberta.generated.restEntities.BaseResponse;
import de.fhg.iais.roberta.generated.restEntities.EntityResponse;
import de.fhg.iais.roberta.generated.restEntities.FullRestRequest;
import de.fhg.iais.roberta.generated.restEntities.ImportErrorResponse;
import de.fhg.iais.roberta.generated.restEntities.ImportRequest;
import de.fhg.iais.roberta.generated.restEntities.ImportResponse;
import de.fhg.iais.roberta.generated.restEntities.ListingNamesResponse;
import de.fhg.iais.roberta.generated.restEntities.ListingResponse;
import de.fhg.iais.roberta.generated.restEntities.SaveRequest;
import de.fhg.iais.roberta.generated.restEntities.SaveResponse;
import de.fhg.iais.roberta.javaServer.provider.XsltTrans;
import de.fhg.iais.roberta.javaServer.restServices.local.LocalProgramStore.Entry;
import de.fhg.iais.roberta.javaServer.restServices.local.LocalProgramStore.StoredProgram;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Statistics;
import de.fhg.iais.roberta.util.UtilForHtmlXml;
import de.fhg.iais.roberta.util.UtilForREST;
import de.fhg.iais.roberta.util.XsltAndJavaTransformer;
import de.fhg.iais.roberta.util.basic.Pair;
import de.fhg.iais.roberta.util.dbc.Assert;
import de.fhg.iais.roberta.util.jaxb.JaxbHelper;

/**
 * The programs of BlockCore: the REST interface of the Open Roberta Lab ("/program/..."), with the programs stored as files of the local user
 * (see {@link LocalProgramStore}) instead of a database. Sharing, the gallery and likes do not exist in BlockCore.
 */
@Path("/program")
public class LocalProgramController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalProgramController.class);

    @POST
    @Path("/save")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveProgram(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            SaveRequest saveRequest = SaveRequest.make(request.getData());
            String programName = saveRequest.getProgramName();
            if ( !LocalProgramStore.isValidName(programName) ) {
                return UtilForREST.makeBaseResponseForError(Key.PROGRAM_ERROR_ID_INVALID, httpSessionState, null);
            }
            LocalProgramStore store = LocalProgramStore.get();
            boolean saveAs = !"save".equals(saveRequest.getCmd());
            if ( saveAs && store.programExists(programName) ) {
                return UtilForREST.makeBaseResponseForError(Key.PROGRAM_SAVE_AS_ERROR_PROGRAM_EXISTS, httpSessionState, null);
            }
            String configText = saveRequest.getConfXML();
            String configName = saveRequest.getConfigName();
            if ( configText == null && configName != null ) {
                // a named configuration without changes: the program file gets a copy of it
                configText = store.loadConfiguration(configName);
            }
            long lastChanged = store.saveProgram(programName, saveRequest.getProgXML(), configText);
            SaveResponse response = SaveResponse.make();
            response.setLastChanged(lastChanged);
            UtilForREST.addSuccessInfo(response, Key.PROGRAM_SAVE_SUCCESS);
            Statistics.info("ProgramSave", "success", true);
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("saving a program failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteProgram(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            String programName = request.getData().getString("programName");
            BaseResponse response = BaseResponse.make();
            if ( LocalProgramStore.get().deleteProgram(programName) ) {
                UtilForREST.addSuccessInfo(response, Key.PROGRAM_DELETE_SUCCESS);
            } else {
                UtilForREST.addErrorInfo(response, Key.PROGRAM_DELETE_ERROR);
            }
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("deleting a program failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/listing")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgram(@XsltTrans XsltAndJavaTransformer xsltAndJavaTransformer, FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            String programName = request.getData().getString("programName");
            StoredProgram program = LocalProgramStore.get().loadProgram(programName);
            if ( program == null ) {
                Statistics.info("ProgramLoad", "failed", false);
                return UtilForREST.makeBaseResponseForError(Key.PROGRAM_GET_ONE_ERROR_NOT_FOUND, httpSessionState, null);
            }
            // programs of older lab versions are brought up to date
            Pair<String, String> transformed = xsltAndJavaTransformer.transform(httpSessionState.getRobotFactory(), program.programXml, program.configXml);
            ListingResponse response = ListingResponse.make();
            response.setProgXML(transformed.getFirst());
            response.setConfXML(transformed.getSecond()); // null: the default configuration
            response.setProgramName(program.name);
            response.setLastChanged(program.lastChanged);
            UtilForREST.addSuccessInfo(response, Key.PROGRAM_GET_ONE_SUCCESS);
            Statistics.info("ProgramLoad", "success", true);
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("loading a program failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/entity")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgramEntity(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            StoredProgram program = LocalProgramStore.get().loadProgram(request.getData().getString("programName"));
            EntityResponse response = EntityResponse.make();
            if ( program == null ) {
                UtilForREST.addErrorInfo(response, Key.PROGRAM_GET_ONE_ERROR_NOT_FOUND);
            } else {
                JSONArray entity = new JSONArray();
                entity.put(robotGroup(httpSessionState));
                entity.put(program.name);
                entity.put(program.programXml);
                entity.put(LocalUserController.ACCOUNT);
                entity.put(program.lastChanged);
                entity.put(0); // views
                entity.put(0); // likes
                entity.put(""); // tags
                response.setProgram(entity);
                UtilForREST.addSuccessInfo(response, Key.PROGRAM_GET_ONE_SUCCESS);
            }
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("loading a program entity failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    @POST
    @Path("/listing/names")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInfosOfProgramsOfLoggedInUser(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            if ( !httpSessionState.isUserLoggedIn() ) {
                return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
            }
            ListingNamesResponse response = ListingNamesResponse.make();
            response.setProgramNames(programInfos(LocalProgramStore.get().listPrograms()));
            UtilForREST.addSuccessInfo(response, Key.PROGRAM_GET_ALL_SUCCESS);
            return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
        } catch ( Exception e ) {
            LOG.error("listing the programs failed", e);
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
    }

    /** [name, owner, sharing (none), author, created, last changed] per program, as the program list of the lab expects it */
    static JSONArray programInfos(List<Entry> programs) {
        JSONArray programInfos = new JSONArray();
        for ( Entry program : programs ) {
            JSONArray programInfo = new JSONArray();
            programInfo.put(program.name);
            programInfo.put(LocalUserController.ACCOUNT);
            programInfo.put(new JSONObject());
            programInfo.put(LocalUserController.ACCOUNT);
            programInfo.put(program.created);
            programInfo.put(program.lastChanged);
            programInfos.put(programInfo);
        }
        return programInfos;
    }

    /** BlockCore has no example programs and no gallery: empty lists */
    @POST
    @Path("/examples/names")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInfosOfExamplePrograms(FullRestRequest request) {
        return emptyList(request);
    }

    @POST
    @Path("/gallery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProgramsFromGallery(FullRestRequest request) {
        return emptyList(request);
    }

    private static Response emptyList(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        ListingNamesResponse response = ListingNamesResponse.make();
        response.setProgramNames(new JSONArray());
        UtilForREST.addSuccessInfo(response, Key.PROGRAM_GET_ALL_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importProgram(@XsltTrans XsltAndJavaTransformer xsltAndJavaTransformer, FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        try {
            ImportRequest importRequest = ImportRequest.make(request.getData());
            String programName = importRequest.getProgramName();
            if ( !LocalProgramStore.isValidName(programName) ) {
                programName = "NEPOprog";
            }
            String xmlText = UtilForHtmlXml.checkProgramTextForXSS(importRequest.getProgXML());
            Export jaxbImportExport = JaxbHelper.xml2Element(xmlText, Export.class);
            Assert.notNull(jaxbImportExport, "import xml is invalid");
            BlockSet jaxbProgram = jaxbImportExport.getProgram().getBlockSet();
            BlockSet jaxbConfig = jaxbImportExport.getConfig().getBlockSet();
            String robotGroup = httpSessionState.getRobotFactory().getGroup();
            if ( jaxbProgram.getRobottype().equals(robotGroup) && jaxbConfig.getRobottype().equals(robotGroup) ) {
                Pair<String, String> transformed =
                    xsltAndJavaTransformer.transform(httpSessionState.getRobotFactory(), JaxbHelper.blockSet2xml(jaxbProgram), JaxbHelper.blockSet2xml(jaxbConfig));
                ImportResponse response = ImportResponse.make();
                response.setProgramName(programName);
                response.setProgXML(transformed.getFirst());
                response.setConfXML(transformed.getSecond());
                UtilForREST.addSuccessInfo(response, Key.PROGRAM_IMPORT_SUCCESS);
                Statistics.info("ProgramImport", "success", true);
                return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
            } else {
                List<RobotFactory> members = httpSessionState.getRobotFactoriesOfGroup(jaxbProgram.getRobottype());
                ImportErrorResponse error = ImportErrorResponse.make();
                error.setRobotTypes(members.stream().map(RobotFactory::getRealName).collect(Collectors.joining(", ")));
                UtilForREST.addErrorInfo(error, Key.PROGRAM_IMPORT_ERROR_WRONG_ROBOT_TYPE);
                Statistics.info("ProgramImport", "success", false);
                return UtilForREST.responseWithFrontendInfo(error, httpSessionState, null);
            }
        } catch ( Exception e ) {
            LOG.error("program import failed. Probably no or user-edited XML", e);
            return UtilForREST.makeBaseResponseForError(Key.PROGRAM_IMPORT_ERROR, httpSessionState, null);
        }
    }

    /** all programs as a zip file (the program files of the store) */
    @GET
    @Path("/exportAllPrograms")
    public Response exportAllPrograms(@QueryParam("initToken") String initToken) {
        HttpSessionState httpSessionState = UtilForREST.validateInitToken(initToken);
        if ( !httpSessionState.isUserLoggedIn() ) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        try {
            LocalProgramStore store = LocalProgramStore.get();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try ( ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8) ) {
                for ( Entry program : store.listPrograms() ) {
                    zip.putNextEntry(new ZipEntry(program.name + ".xml"));
                    zip.write(Files.readAllBytes(store.getDir().resolve(program.name + ".xml")));
                    zip.closeEntry();
                }
            }
            return Response.ok(bytes.toByteArray(), "application/zip").header("Content-Disposition", "attachment; filename=\"BlockCore_Programs.zip\"").build();
        } catch ( Exception e ) {
            LOG.error("exporting all programs failed", e);
            return Response.serverError().build();
        }
    }

    /** sharing, likes and the program lists of user groups do not exist in BlockCore */
    @POST
    @Path("/{notAvailable: share|share/create|share/delete|like|userGroupMembers/names}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response notAvailable(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        return UtilForREST.makeBaseResponseForError(Key.COMMAND_INVALID, httpSessionState, null);
    }

    private static String robotGroup(HttpSessionState httpSessionState) {
        RobotFactory factory = httpSessionState.getRobotFactory(httpSessionState.getRobotName());
        return factory.getGroup().isEmpty() ? httpSessionState.getRobotName() : factory.getGroup();
    }
}
