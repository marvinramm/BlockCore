/*
 * Launcher for the standalone Open Roberta Lab on Windows (OpenRobertaLab.exe).
 *
 * Layout next to the exe:
 *   jre\            Java runtime
 *   lib\            server jars
 *   staticResources\ the web frontend
 *   ora-cc-rsc\     cross compiler resources (AVR toolchain, avrdude, Arduino cores and libraries)
 *
 * User data (database, logs) goes to %LOCALAPPDATA%\OpenRobertaLab, because the program directory may not be writable.
 *
 * What it does:
 *   1. if the lab is already running (port in use), only the browser is opened
 *   2. on first start an empty database is created
 *   3. the server is started (output -> logs\server.log); it is killed when this window is closed (job object)
 *   4. as soon as the server answers, the browser is opened
 *
 * Build (on Linux): see build-windows.sh
 */
#define WIN32_LEAN_AND_MEAN
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <winsock2.h>
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <stdio.h>
#include <wchar.h>

#define PORT 1999
#define START_TIMEOUT_SECONDS 180
#define CMD_MAX 32768

static wchar_t appDir[MAX_PATH];
static wchar_t dataDir[MAX_PATH];

static void say(const wchar_t *text) {
    DWORD written;
    HANDLE out = GetStdHandle(STD_OUTPUT_HANDLE);
    WriteConsoleW(out, text, (DWORD) wcslen(text), &written, NULL);
}

static void fail(const wchar_t *text) {
    say(L"\r\nFEHLER: ");
    say(text);
    say(L"\r\n\r\nDetails stehen in %LOCALAPPDATA%\\OpenRobertaLab\\logs\\server.log\r\nTaste druecken zum Beenden ...");
    getchar();
    ExitProcess(1);
}

static BOOL portInUse(void) {
    SOCKET s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    struct sockaddr_in addr;
    BOOL connected;
    if ( s == INVALID_SOCKET ) {
        return FALSE;
    }
    ZeroMemory(&addr, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    connected = connect(s, (struct sockaddr *) &addr, sizeof(addr)) == 0;
    closesocket(s);
    return connected;
}

static void openBrowser(void) {
    wchar_t url[128];
    swprintf(url, 128, L"http://127.0.0.1:%d/#loadSystem&&uno", PORT);
    ShellExecuteW(NULL, L"open", url, NULL, NULL, SW_SHOWNORMAL);
}

/* appends one argument in double quotes (no argument contains a double quote) */
static void addArg(wchar_t *cmd, const wchar_t *arg) {
    if ( cmd[0] != 0 ) {
        wcscat_s(cmd, CMD_MAX, L" ");
    }
    wcscat_s(cmd, CMD_MAX, L"\"");
    wcscat_s(cmd, CMD_MAX, arg);
    wcscat_s(cmd, CMD_MAX, L"\"");
}

static void addProperty(wchar_t *cmd, const wchar_t *name, const wchar_t *value) {
    wchar_t property[2 * MAX_PATH];
    swprintf(property, 2 * MAX_PATH, L"%ls=%ls", name, value);
    addArg(cmd, L"-d");
    addArg(cmd, property);
}

static void addJavaStart(wchar_t *cmd, const wchar_t *mainClass) {
    wchar_t path[MAX_PATH];
    swprintf(path, MAX_PATH, L"%ls\\jre\\bin\\java.exe", appDir);
    addArg(cmd, path);
    addArg(cmd, L"--add-opens");
    addArg(cmd, L"java.base/java.lang=ALL-UNNAMED");
    addArg(cmd, L"-Dfile.encoding=UTF-8");
    addArg(cmd, L"-cp");
    swprintf(path, MAX_PATH, L"%ls\\lib\\*", appDir);
    addArg(cmd, path);
    addArg(cmd, mainClass);
}

static HANDLE startJava(wchar_t *cmd, HANDLE log, HANDLE job) {
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    si.hStdOutput = log;
    si.hStdError = log;
    if ( !CreateProcessW(NULL, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, dataDir, &si, &pi) ) {
        return NULL;
    }
    if ( job != NULL ) {
        AssignProcessToJobObject(job, pi.hProcess);
    }
    CloseHandle(pi.hThread);
    return pi.hProcess;
}

static HANDLE openLog(void) {
    wchar_t path[MAX_PATH];
    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    swprintf(path, MAX_PATH, L"%ls\\logs", dataDir);
    CreateDirectoryW(path, NULL);
    swprintf(path, MAX_PATH, L"%ls\\logs\\server.log", dataDir);
    return CreateFileW(path, FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, &sa, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
}

int wmain(void) {
    wchar_t *cmd;
    wchar_t path[MAX_PATH];
    wchar_t localAppData[MAX_PATH];
    wchar_t *lastSlash;
    HANDLE log, job, server;
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;
    WSADATA wsa;
    int seconds;

    SetConsoleTitleW(L"Open Roberta Lab");
    SetConsoleOutputCP(CP_UTF8);
    say(L"Open Roberta Lab\r\n================\r\n\r\n");

    WSAStartup(MAKEWORD(2, 2), &wsa);
    if ( portInUse() ) {
        say(L"Das Lab laeuft bereits. Der Browser wird geoeffnet.\r\n");
        openBrowser();
        Sleep(2000);
        return 0;
    }

    GetModuleFileNameW(NULL, appDir, MAX_PATH);
    lastSlash = wcsrchr(appDir, L'\\');
    if ( lastSlash != NULL ) {
        *lastSlash = 0;
    }
    if ( SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE, NULL, 0, localAppData) != S_OK ) {
        fail(L"Der Ordner %LOCALAPPDATA% wurde nicht gefunden.");
    }
    swprintf(dataDir, MAX_PATH, L"%ls\\OpenRobertaLab", localAppData);
    CreateDirectoryW(dataDir, NULL);
    swprintf(path, MAX_PATH, L"%ls\\db", dataDir);
    CreateDirectoryW(path, NULL);

    log = openLog();
    if ( log == INVALID_HANDLE_VALUE ) {
        fail(L"Die Logdatei kann nicht geschrieben werden.");
    }

    /* the server dies together with this launcher (window closed, Ctrl+C, ...) */
    job = CreateJobObjectW(NULL, NULL);
    if ( job != NULL ) {
        ZeroMemory(&limits, sizeof(limits));
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
    }

    cmd = (wchar_t *) calloc(CMD_MAX, sizeof(wchar_t));

    swprintf(path, MAX_PATH, L"%ls\\db\\openroberta-db.script", dataDir);
    if ( GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES ) {
        DWORD exitCode = 1;
        say(L"Erster Start: die Datenbank wird angelegt ...\r\n");
        addJavaStart(cmd, L"de.fhg.iais.roberta.main.Administration");
        addArg(cmd, L"create-empty-db");
        swprintf(path, MAX_PATH, L"jdbc:hsqldb:file:%ls\\db\\openroberta-db", dataDir);
        addArg(cmd, path);
        server = startJava(cmd, log, job);
        if ( server == NULL ) {
            fail(L"Java konnte nicht gestartet werden.");
        }
        WaitForSingleObject(server, INFINITE);
        GetExitCodeProcess(server, &exitCode);
        CloseHandle(server);
        if ( exitCode != 0 ) {
            fail(L"Die Datenbank konnte nicht angelegt werden.");
        }
    }

    cmd[0] = 0;
    addJavaStart(cmd, L"de.fhg.iais.roberta.main.ServerStarter");
    addProperty(cmd, L"database.mode", L"embedded");
    swprintf(path, MAX_PATH, L"%ls\\db", dataDir);
    addProperty(cmd, L"database.parentdir", path);
    addProperty(cmd, L"database.name", L"openroberta-db");
    swprintf(path, MAX_PATH, L"%ls\\staticResources", appDir);
    addProperty(cmd, L"server.staticresources.dir", path);
    swprintf(path, MAX_PATH, L"%ls\\ora-cc-rsc", appDir);
    addProperty(cmd, L"robot.crosscompiler.resourcebase", path);
    addProperty(cmd, L"server.ip", L"127.0.0.1");
    swprintf(path, MAX_PATH, L"%d", PORT);
    addProperty(cmd, L"server.port", path);
    addProperty(cmd, L"server.local.serial", L"true");
    addProperty(cmd, L"robot.whitelist", L"uno, nano, mega");
    addProperty(cmd, L"robot.default", L"uno");
    addProperty(cmd, L"server.admin.dir", dataDir);

    say(L"Der Server wird gestartet ...\r\n");
    server = startJava(cmd, log, job);
    free(cmd);
    if ( server == NULL ) {
        fail(L"Java konnte nicht gestartet werden.");
    }

    for ( seconds = 0; seconds < START_TIMEOUT_SECONDS; seconds++ ) {
        if ( WaitForSingleObject(server, 1000) == WAIT_OBJECT_0 ) {
            fail(L"Der Server wurde unerwartet beendet.");
        }
        if ( portInUse() ) {
            break;
        }
    }
    if ( seconds >= START_TIMEOUT_SECONDS ) {
        fail(L"Der Server hat nicht rechtzeitig geantwortet.");
    }

    say(L"\r\nDas Lab laeuft: http://127.0.0.1:1999\r\n");
    say(L"Der Browser wird geoeffnet. Roboter per USB anschliessen und im Lab unter\r\n");
    say(L"\"Roboter -> Verbinden\" den COM-Port waehlen.\r\n\r\n");
    say(L">>> Zum Beenden dieses Fenster schliessen. <<<\r\n");
    openBrowser();

    WaitForSingleObject(server, INFINITE);
    say(L"\r\nDer Server wurde beendet.\r\n");
    Sleep(3000);
    return 0;
}
