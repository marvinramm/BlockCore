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
import de.fhg.iais.roberta.generated.restEntities.FullRestRequest;
import de.fhg.iais.roberta.generated.restEntities.GetStatusTextResponse;
import de.fhg.iais.roberta.generated.restEntities.GetUserResponse;
import de.fhg.iais.roberta.generated.restEntities.LoginResponse;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.UtilForREST;

/**
 * The user of BlockCore: there is exactly one, the person at this computer. The frontend of the lab saves programs only for a logged-in user,
 * so this controller answers the login (of any account) with this local user. Accounts, passwords, e-mails and user groups do not exist.
 */
@Path("/user")
public class LocalUserController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalUserController.class);
    static final String ACCOUNT = "blockcore";
    static final int USER_ID = 2;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        if ( httpSessionState.isUserLoggedIn() ) {
            return UtilForREST.makeBaseResponseForError(Key.COMMAND_INVALID, httpSessionState, null);
        }
        httpSessionState.setUserClearDataKeepTokenAndRobotId(USER_ID);
        LoginResponse response = LoginResponse.make();
        response.setCmd("login");
        response.setUserId(USER_ID);
        response.setUserRole("TEACHER");
        response.setUserAccountName(ACCOUNT);
        response.setUserName("BlockCore");
        response.setIsAccountActivated(true);
        response.setUserGroupName("");
        response.setUserGroupOwner("");
        UtilForREST.addSuccessInfo(response, Key.USER_GET_ONE_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        if ( !httpSessionState.isUserLoggedIn() ) {
            return UtilForREST.makeBaseResponseForError(Key.COMMAND_INVALID, httpSessionState, null);
        }
        httpSessionState.setUserClearDataKeepTokenAndRobotId(HttpSessionState.NO_USER);
        BaseResponse response = BaseResponse.make();
        response.setCmd("logout");
        UtilForREST.addSuccessInfo(response, Key.USER_LOGOUT_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    /** the frontend was reloaded: the session forgets the user (the next login brings it back) */
    @POST
    @Path("/clear")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response clear(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        httpSessionState.setUserClearDataKeepTokenAndRobotId(HttpSessionState.NO_USER);
        BaseResponse response = BaseResponse.make();
        response.setCmd("clear");
        UtilForREST.addSuccessInfo(response, Key.INIT_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    @POST
    @Path("/loggedInCheck")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loggedInCheck(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        if ( !httpSessionState.isUserLoggedIn() ) {
            return UtilForREST.makeBaseResponseForError(Key.USER_ERROR_NOT_LOGGED_IN, httpSessionState, null);
        }
        BaseResponse response = BaseResponse.make();
        UtilForREST.addSuccessInfo(response, Key.SERVER_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    @POST
    @Path("/getUser")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        if ( !httpSessionState.isUserLoggedIn() ) {
            return UtilForREST.makeBaseResponseForError(Key.SERVER_ERROR, httpSessionState, null);
        }
        GetUserResponse response = GetUserResponse.make();
        response.setCmd("getUser");
        response.setUserId(USER_ID);
        response.setUserAccountName(ACCOUNT);
        response.setUserName("BlockCore");
        response.setUserEmail("");
        response.setIsYoungerThen14(false);
        UtilForREST.addSuccessInfo(response, Key.USER_GET_ONE_SUCCESS);
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    @POST
    @Path("/getStatusText")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatusText(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        GetStatusTextResponse response = GetStatusTextResponse.make();
        response.setCmd("getStatusText");
        response.setStatustext(new JSONArray().put("").put(""));
        response.setRc("ok");
        return UtilForREST.responseWithFrontendInfo(response, httpSessionState, null);
    }

    /** accounts, passwords and e-mails do not exist in BlockCore */
    @POST
    @Path("/{notAvailable: createUser|updateUser|changePassword|passwordRecovery|isResetPasswordLinkExpired|resetPassword|activateUser|resendActivation|deleteUser|setStatusText}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response notAvailable(FullRestRequest request) {
        HttpSessionState httpSessionState = UtilForREST.handleRequestInit(LOG, request, true);
        return UtilForREST.makeBaseResponseForError(Key.COMMAND_INVALID, httpSessionState, null);
    }
}
