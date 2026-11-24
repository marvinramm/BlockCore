/*
 * Launcher of BlockCore, the standalone block programming lab for Arduino on Windows (BlockCore.exe).
 * BlockCore is based on the Open Roberta Lab of Fraunhofer IAIS.
 *
 * Layout next to the exe:
 *   jre\             Java runtime
 *   lib\             server jars
 *   staticResources\ the web frontend
 *   ora-cc-rsc\      cross compiler resources (AVR toolchain, avrdude, Arduino cores and libraries)
 *
 * User data (database, logs, window profile) goes to %LOCALAPPDATA%\BlockCore, because the program directory may not be writable.
 *
 * What it does (no console window):
 *   1. a small start window is shown while the server starts
 *   2. on first start an empty database is created
 *   3. the server is started hidden (output -> logs\server.log)
 *   4. the lab opens in its own window: Microsoft Edge or Google Chrome in app mode with a profile of its own
 *   5. when all BlockCore windows (lab and serial monitor) are closed, the server is stopped (found by their title)
 *   If neither Edge nor Chrome is found, the default browser is used and a small dialog stops BlockCore.
 *   If BlockCore is already running, only a new window is opened.
 *
 * Build (on Linux): see build-windows.sh
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <stdio.h>
#include <wchar.h>

#define PORT 1999
#define START_TIMEOUT_SECONDS 180
#define CMD_MAX 32768
#define WM_APP_STATUS (WM_APP + 1)
#define WM_APP_READY (WM_APP + 2)
#define WM_APP_FAILED (WM_APP + 3)

static wchar_t appDir[MAX_PATH];
static wchar_t dataDir[MAX_PATH];
static wchar_t status[256] = L"wird gestartet ...";
static wchar_t failure[512];
static HWND splash;
static HANDLE job;
static HANDLE server;
static HICON icon;

/* ---------------------------------------------------------------- helpers */

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

static void showError(const wchar_t *text) {
    wchar_t message[1024];
    swprintf(message, 1024, L"%ls\n\nDetails stehen in\n%ls\\logs\\server.log", text, dataDir[0] ? dataDir : L"%LOCALAPPDATA%\\BlockCore");
    MessageBoxW(NULL, message, L"BlockCore", MB_OK | MB_ICONERROR);
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
    /* fast start, small memory footprint: the lab compiles small programs only */
    addArg(cmd, L"-XX:+UseSerialGC");
    addArg(cmd, L"-XX:TieredStopAtLevel=1");
    addArg(cmd, L"-Xss2m");
    addArg(cmd, L"-Xmx768m");
    addArg(cmd, L"--add-opens");
    addArg(cmd, L"java.base/java.lang=ALL-UNNAMED");
    addArg(cmd, L"-Dfile.encoding=UTF-8");
    addArg(cmd, L"-cp");
    swprintf(path, MAX_PATH, L"%ls\\lib\\*", appDir);
    addArg(cmd, path);
    addArg(cmd, mainClass);
}

static HANDLE startHidden(wchar_t *cmd, HANDLE log) {
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = NULL;
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
    wchar_t old[MAX_PATH];
    SECURITY_ATTRIBUTES sa;
    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    swprintf(path, MAX_PATH, L"%ls\\logs", dataDir);
    CreateDirectoryW(path, NULL);
    swprintf(path, MAX_PATH, L"%ls\\logs\\server.log", dataDir);
    /* a new log for every start, the previous one is kept as server.old.log */
    swprintf(old, MAX_PATH, L"%ls\\logs\\server.old.log", dataDir);
    MoveFileExW(path, old, MOVEFILE_REPLACE_EXISTING);
    return CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, &sa, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
}

/* ---------------------------------------------------------------- the lab window */

static BOOL findBrowser(wchar_t *exe) {
    static const wchar_t *programFiles[] = {L"ProgramFiles(x86)", L"ProgramFiles", L"LOCALAPPDATA"};
    static const wchar_t *browsers[] = {L"Microsoft\\Edge\\Application\\msedge.exe", L"Google\\Chrome\\Application\\chrome.exe"};
    wchar_t base[MAX_PATH];
    int b, p;
    for ( b = 0; b < 2; b++ ) {
        for ( p = 0; p < 3; p++ ) {
            if ( GetEnvironmentVariableW(programFiles[p], base, MAX_PATH) == 0 ) {
                continue;
            }
            swprintf(exe, MAX_PATH, L"%ls\\%ls", base, browsers[b]);
            if ( GetFileAttributesW(exe) != INVALID_FILE_ATTRIBUTES ) {
                return TRUE;
            }
        }
    }
    return FALSE;
}

/*
 * opens the lab as an app window (no address bar, no tabs). The browser gets a profile of its own, so its process ends when the
 * BlockCore windows are closed.
 *
 * @return the browser process, or NULL if no app window could be opened
 */
static HANDLE openAppWindow(void) {
    wchar_t exe[MAX_PATH];
    wchar_t arg[2 * MAX_PATH];
    wchar_t *cmd;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    if ( !findBrowser(exe) ) {
        return NULL;
    }
    cmd = (wchar_t *) calloc(CMD_MAX, sizeof(wchar_t));
    addArg(cmd, exe);
    swprintf(arg, 2 * MAX_PATH, L"--app=http://127.0.0.1:%d/?loadSystem=uno", PORT);
    addArg(cmd, arg);
    swprintf(arg, 2 * MAX_PATH, L"--user-data-dir=%ls\\window", dataDir);
    addArg(cmd, arg);
    addArg(cmd, L"--no-first-run");
    addArg(cmd, L"--no-default-browser-check");
    addArg(cmd, L"--disable-background-mode");
    addArg(cmd, L"--disable-features=Translate,msEdgeSidebarV2,msHubApps,msUndersideButton");
    addArg(cmd, L"--window-size=1400,900");
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    if ( !CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi) ) {
        free(cmd);
        return NULL;
    }
    free(cmd);
    CloseHandle(pi.hThread);
    return pi.hProcess;
}

static void openInDefaultBrowser(void) {
    wchar_t url[128];
    swprintf(url, 128, L"http://127.0.0.1:%d/?loadSystem=uno", PORT);
    ShellExecuteW(NULL, L"open", url, NULL, NULL, SW_SHOWNORMAL);
}

/*
 * counts the top level browser windows whose title starts with "BlockCore" (the lab and the serial monitor). The browser process cannot
 * be watched: Edge may hand the window over to another of its processes and end the one that was started.
 */
static BOOL CALLBACK countWindow(HWND hwnd, LPARAM counter) {
    wchar_t title[64];
    wchar_t className[64];
    if ( !IsWindowVisible(hwnd) ) {
        return TRUE;
    }
    if ( GetClassNameW(hwnd, className, 64) == 0 || wcsncmp(className, L"Chrome_WidgetWin", 16) != 0 ) {
        return TRUE;
    }
    if ( GetWindowTextW(hwnd, title, 64) > 0 && wcsncmp(title, L"BlockCore", 9) == 0 ) {
        (*(int *) counter)++;
    }
    return TRUE;
}

static int countBlockCoreWindows(void) {
    int counter = 0;
    EnumWindows(countWindow, (LPARAM) &counter);
    return counter;
}

/* ---------------------------------------------------------------- start window */

static LRESULT CALLBACK splashProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if ( msg == WM_PAINT ) {
        PAINTSTRUCT ps;
        RECT rc;
        HDC dc = BeginPaint(hwnd, &ps);
        HFONT title = CreateFontW(-34, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
        HFONT text = CreateFontW(-16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
        HGDIOBJ old;
        GetClientRect(hwnd, &rc);
        FillRect(dc, &rc, (HBRUSH) GetStockObject(WHITE_BRUSH));
        if ( icon != NULL ) {
            DrawIconEx(dc, 36, 52, icon, 96, 96, 0, NULL, DI_NORMAL);
        }
        SetBkMode(dc, TRANSPARENT);
        old = SelectObject(dc, title);
        SetTextColor(dc, RGB(0x39, 0x40, 0x4a));
        TextOutW(dc, 156, 62, L"BlockCore", 9);
        SelectObject(dc, text);
        SetTextColor(dc, RGB(0x6c, 0x75, 0x7d));
        TextOutW(dc, 158, 110, status, (int) wcslen(status));
        SelectObject(dc, old);
        DeleteObject(title);
        DeleteObject(text);
        EndPaint(hwnd, &ps);
        return 0;
    }
    if ( msg == WM_APP_STATUS ) {
        InvalidateRect(hwnd, NULL, TRUE);
        return 0;
    }
    if ( msg == WM_APP_READY || msg == WM_APP_FAILED ) {
        PostQuitMessage((int) msg);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

static void createSplash(HINSTANCE instance) {
    WNDCLASSW wc;
    int width = 440, height = 200;
    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = splashProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursor(NULL, IDC_APPSTARTING);
    wc.hIcon = icon;
    wc.lpszClassName = L"BlockCoreSplash";
    RegisterClassW(&wc);
    splash = CreateWindowExW(WS_EX_APPWINDOW, L"BlockCoreSplash", L"BlockCore", WS_POPUP | WS_BORDER,
        (GetSystemMetrics(SM_CXSCREEN) - width) / 2, (GetSystemMetrics(SM_CYSCREEN) - height) / 2, width, height, NULL, NULL, instance, NULL);
    ShowWindow(splash, SW_SHOW);
    UpdateWindow(splash);
}

static void setStatus(const wchar_t *text) {
    wcsncpy(status, text, 255);
    PostMessageW(splash, WM_APP_STATUS, 0, 0);
}

static DWORD fail(const wchar_t *text) {
    wcsncpy(failure, text, 511);
    PostMessageW(splash, WM_APP_FAILED, 0, 0);
    return 1;
}

/* ---------------------------------------------------------------- server start (worker thread) */

static DWORD WINAPI startServer(LPVOID unused) {
    wchar_t *cmd = (wchar_t *) calloc(CMD_MAX, sizeof(wchar_t));
    wchar_t path[MAX_PATH];
    HANDLE log = openLog();
    int halfSeconds;
    (void) unused;
    if ( log == INVALID_HANDLE_VALUE ) {
        return fail(L"Die Logdatei kann nicht geschrieben werden.");
    }

    swprintf(path, MAX_PATH, L"%ls\\db\\openroberta-db.script", dataDir);
    if ( GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES ) {
        HANDLE create;
        DWORD exitCode = 1;
        setStatus(L"Erster Start: die Datenbank wird angelegt ...");
        addJavaStart(cmd, L"de.fhg.iais.roberta.main.Administration");
        addArg(cmd, L"create-empty-db");
        swprintf(path, MAX_PATH, L"jdbc:hsqldb:file:%ls\\db\\openroberta-db", dataDir);
        addArg(cmd, path);
        create = startHidden(cmd, log);
        if ( create == NULL ) {
            return fail(L"Java konnte nicht gestartet werden.");
        }
        WaitForSingleObject(create, INFINITE);
        GetExitCodeProcess(create, &exitCode);
        CloseHandle(create);
        if ( exitCode != 0 ) {
            return fail(L"Die Datenbank konnte nicht angelegt werden.");
        }
    }

    setStatus(L"wird gestartet ...");
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
    server = startHidden(cmd, log);
    free(cmd);
    if ( server == NULL ) {
        return fail(L"Java konnte nicht gestartet werden.");
    }

    for ( halfSeconds = 0; halfSeconds < 2 * START_TIMEOUT_SECONDS; halfSeconds++ ) {
        if ( WaitForSingleObject(server, 500) == WAIT_OBJECT_0 ) {
            return fail(L"Der BlockCore-Server wurde unerwartet beendet.");
        }
        if ( portInUse() ) {
            PostMessageW(splash, WM_APP_READY, 0, 0);
            return 0;
        }
    }
    return fail(L"Der BlockCore-Server hat nicht rechtzeitig geantwortet.");
}

/* ---------------------------------------------------------------- main */

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE prev, LPWSTR cmdLine, int show) {
    wchar_t path[MAX_PATH];
    wchar_t localAppData[MAX_PATH];
    wchar_t *lastSlash;
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;
    WSADATA wsa;
    HANDLE instanceMutex;
    HANDLE window;
    MSG msg;
    (void) prev;
    (void) cmdLine;
    (void) show;

    icon = (HICON) LoadImageW(instance, MAKEINTRESOURCEW(1), IMAGE_ICON, 96, 96, 0);
    WSAStartup(MAKEWORD(2, 2), &wsa);
    GetModuleFileNameW(NULL, appDir, MAX_PATH);
    lastSlash = wcsrchr(appDir, L'\\');
    if ( lastSlash != NULL ) {
        *lastSlash = 0;
    }
    if ( SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE, NULL, 0, localAppData) != S_OK ) {
        showError(L"Der Ordner %LOCALAPPDATA% wurde nicht gefunden.");
        return 1;
    }
    swprintf(dataDir, MAX_PATH, L"%ls\\BlockCore", localAppData);
    CreateDirectoryW(dataDir, NULL);
    swprintf(path, MAX_PATH, L"%ls\\db", dataDir);
    CreateDirectoryW(path, NULL);

    /* already running (or just starting): open another window only */
    instanceMutex = CreateMutexW(NULL, TRUE, L"Local\\BlockCoreLauncher");
    if ( GetLastError() == ERROR_ALREADY_EXISTS || portInUse() ) {
        int i;
        for ( i = 0; i < 2 * START_TIMEOUT_SECONDS && !portInUse(); i++ ) {
            Sleep(500);
        }
        window = openAppWindow();
        if ( window == NULL ) {
            openInDefaultBrowser();
        } else {
            CloseHandle(window);
        }
        return 0;
    }

    /* the server dies together with this launcher */
    job = CreateJobObjectW(NULL, NULL);
    if ( job != NULL ) {
        ZeroMemory(&limits, sizeof(limits));
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
    }

    createSplash(instance);
    CloseHandle(CreateThread(NULL, 0, startServer, NULL, 0, NULL));
    while ( GetMessageW(&msg, NULL, 0, 0) > 0 ) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    if ( (UINT) msg.wParam == WM_APP_FAILED ) {
        DestroyWindow(splash);
        showError(failure);
        if ( job != NULL ) {
            TerminateJobObject(job, 1);
        }
        return 1;
    }

    window = openAppWindow();
    if ( window != NULL ) {
        int halfSeconds;
        int missing = 0;
        BOOL seen = FALSE;
        CloseHandle(window);
        /* keep the start window until the lab window is there (at most 60 s) */
        for ( halfSeconds = 0; halfSeconds < 120 && !seen; halfSeconds++ ) {
            seen = countBlockCoreWindows() > 0;
            if ( !seen ) {
                MsgWaitForMultipleObjects(0, NULL, FALSE, 500, QS_ALLINPUT);
                while ( PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE) ) {
                    DispatchMessageW(&msg);
                }
            }
        }
        DestroyWindow(splash);
        if ( seen ) {
            /* stop the server when all BlockCore windows are closed (checked every second, 3 s grace for reloads) */
            while ( missing < 3 ) {
                Sleep(1000);
                missing = countBlockCoreWindows() > 0 ? 0 : missing + 1;
            }
        } else {
            MessageBoxW(NULL, L"BlockCore läuft (http://127.0.0.1:1999).\n\nZum Beenden von BlockCore auf OK klicken.", L"BlockCore",
                MB_OK | MB_ICONINFORMATION);
        }
    } else {
        DestroyWindow(splash);
        openInDefaultBrowser();
        MessageBoxW(NULL, L"BlockCore läuft im Browser (http://127.0.0.1:1999).\n\nZum Beenden von BlockCore auf OK klicken.", L"BlockCore",
            MB_OK | MB_ICONINFORMATION);
    }

    if ( job != NULL ) {
        TerminateJobObject(job, 0);
    }
    if ( instanceMutex != NULL ) {
        CloseHandle(instanceMutex);
    }
    return 0;
}
