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
 *   5. when all BlockCore windows (lab and serial monitor) are closed, the server is stopped (found by their title): the launcher closes the
 *      standard input of the server, the server ends normally (database closed cleanly) and Java writes its class data archive
 *      (%LOCALAPPDATA%\BlockCore\cds): the next start is faster. A server that does not end within 15 s is killed.
 *   6. update: if the server ends with exit code 42, it has downloaded a new installer (see LocalUpdate.java). The windows are closed and
 *      the installer is started with /UPDATE; it installs without questions and starts BlockCore again.
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
#include <math.h>

#define PORT 1999
#define START_TIMEOUT_SECONDS 180
#define CMD_MAX 32768
#define WM_APP_STATUS (WM_APP + 1)
#define WM_APP_READY (WM_APP + 2)
#define WM_APP_FAILED (WM_APP + 3)
#define EXIT_CODE_UPDATE 42
#define ANIMATION_TIMER 1

static wchar_t appDir[MAX_PATH];
static wchar_t dataDir[MAX_PATH];
static wchar_t status[256] = L"Starting \x2026";
static wchar_t failure[512];
static HWND splash;
static HANDLE job;
static HANDLE server;
static HANDLE serverInput; /* write end of the pipe to the standard input of the server: closing it stops the server */
static HICON icon;
static DWORD splashStart;

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
    swprintf(message, 1024, L"%ls\n\nDetails are in\n%ls\\logs\\server.log", text, dataDir[0] ? dataDir : L"%LOCALAPPDATA%\\BlockCore");
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

static void addJavaStart(wchar_t *cmd, const wchar_t *mainClass, BOOL classDataSharing) {
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
    if ( classDataSharing ) {
        /* the classes loaded during a session are stored when the server ends, the next start maps them (about 40 % faster) */
        swprintf(path, MAX_PATH, L"-XX:SharedArchiveFile=%ls\\cds\\blockcore.jsa", dataDir);
        addArg(cmd, L"-XX:+AutoCreateSharedArchive");
        addArg(cmd, path);
        addArg(cmd, L"-Xlog:cds=off");
        addArg(cmd, L"-Xlog:cds+dynamic=off");
    }
    addArg(cmd, L"-cp");
    swprintf(path, MAX_PATH, L"%ls\\lib\\*", appDir);
    addArg(cmd, path);
    addArg(cmd, mainClass);
}

static HANDLE startHidden(wchar_t *cmd, HANDLE input, HANDLE log) {
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdInput = input;
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

/* ---------------------------------------------------------------- start window (animated) */

#define SPLASH_W 500
#define SPLASH_H 270

static double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
}

/* ease out with a small overshoot */
static double easeOutBack(double t) {
    double c = 1.4;
    t = clamp01(t) - 1;
    return 1 + (c + 1) * t * t * t + c * t * t;
}

static COLORREF mix(COLORREF a, COLORREF b, double t) {
    t = clamp01(t);
    return RGB((int) (GetRValue(a) + (GetRValue(b) - GetRValue(a)) * t), (int) (GetGValue(a) + (GetGValue(b) - GetGValue(a)) * t),
        (int) (GetBValue(a) + (GetBValue(b) - GetBValue(a)) * t));
}

static void fillRound(HDC dc, int x, int y, int w, int h, int r, COLORREF color) {
    HBRUSH brush = CreateSolidBrush(color);
    HPEN pen = CreatePen(PS_SOLID, 1, color);
    HGDIOBJ oldBrush = SelectObject(dc, brush);
    HGDIOBJ oldPen = SelectObject(dc, pen);
    RoundRect(dc, x, y, x + w, y + h, r, r);
    SelectObject(dc, oldBrush);
    SelectObject(dc, oldPen);
    DeleteObject(brush);
    DeleteObject(pen);
}

/* one block of the logo: a bar with the notch of a puzzle block */
static void drawBlock(HDC dc, int x, int y, int w, COLORREF color) {
    fillRound(dc, x, y, w, 30, 10, color);
    fillRound(dc, x + 14, y + 26, 22, 9, 6, color);
    fillRound(dc, x + 14, y - 4, 22, 8, 6, RGB(255, 255, 255));
}

static void paintSplash(HDC dc, RECT *rc) {
    static const COLORREF colors[3] = {RGB(0xba, 0xcc, 0x1e), RGB(0xf2, 0x94, 0x00), RGB(0x2f, 0x8f, 0xbf)};
    static const int offsets[3] = {0, 16, 0};
    double t = (GetTickCount() - splashStart) / 1000.0;
    HFONT bold = CreateFontW(-46, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
    HFONT light = CreateFontW(-46, 0, 0, 0, FW_LIGHT, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI Light");
    HFONT small = CreateFontW(-15, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Segoe UI");
    HGDIOBJ old;
    int i, barX = 48, barY = 218, barW = SPLASH_W - 96;
    double p, segment, pos;

    FillRect(dc, rc, (HBRUSH) GetStockObject(WHITE_BRUSH));

    /* the three blocks fall into place one after the other */
    for ( i = 0; i < 3; i++ ) {
        p = easeOutBack((t - 0.12 * i) / 0.55);
        if ( t - 0.12 * i <= 0 ) {
            continue;
        }
        drawBlock(dc, 96 + offsets[i] - (int) ((1 - p) * 60), 40 + 46 * i, 120, mix(RGB(255, 255, 255), colors[i], clamp01((t - 0.12 * i) / 0.3)));
    }

    /* the name fades in */
    SetBkMode(dc, TRANSPARENT);
    p = clamp01((t - 0.35) / 0.5);
    old = SelectObject(dc, bold);
    SetTextColor(dc, mix(RGB(255, 255, 255), RGB(0x39, 0x40, 0x4a), p));
    TextOutW(dc, 262 + (int) ((1 - p) * 20), 44, L"Block", 5);
    SelectObject(dc, light);
    TextOutW(dc, 262 + (int) ((1 - p) * 30), 94, L"Core", 4);

    /* status */
    SelectObject(dc, small);
    SetTextColor(dc, mix(RGB(255, 255, 255), RGB(0x6c, 0x75, 0x7d), clamp01((t - 0.6) / 0.4)));
    TextOutW(dc, barX, barY - 28, status, (int) wcslen(status));
    SelectObject(dc, old);

    /* moving bar in the colours of the logo */
    fillRound(dc, barX, barY, barW, 6, 6, RGB(0xee, 0xf0, 0xe4));
    if ( t > 0.6 ) {
        segment = barW * 0.28;
        pos = fmod((t - 0.6) * 0.75, 1.0);
        pos = pos < 0.5 ? 2 * pos * pos : 1 - 2 * (1 - pos) * (1 - pos); /* ease in and out */
        {
            int x = barX + (int) (pos * (barW + segment)) - (int) segment;
            int from = x < barX ? barX : x;
            int to = x + (int) segment > barX + barW ? barX + barW : x + (int) segment;
            int colorIndex = (int) ((t - 0.6) * 0.75) % 3;
            if ( to > from ) {
                fillRound(dc, from, barY, to - from, 6, 6, colors[colorIndex]);
            }
        }
    }
    DeleteObject(bold);
    DeleteObject(light);
    DeleteObject(small);
}

static LRESULT CALLBACK splashProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if ( msg == WM_PAINT ) {
        PAINTSTRUCT ps;
        RECT rc;
        HDC dc = BeginPaint(hwnd, &ps);
        HDC buffer = CreateCompatibleDC(dc);
        HBITMAP bitmap;
        HGDIOBJ oldBitmap;
        GetClientRect(hwnd, &rc);
        bitmap = CreateCompatibleBitmap(dc, rc.right, rc.bottom);
        oldBitmap = SelectObject(buffer, bitmap);
        paintSplash(buffer, &rc);
        BitBlt(dc, 0, 0, rc.right, rc.bottom, buffer, 0, 0, SRCCOPY);
        SelectObject(buffer, oldBitmap);
        DeleteObject(bitmap);
        DeleteDC(buffer);
        EndPaint(hwnd, &ps);
        return 0;
    }
    if ( msg == WM_ERASEBKGND ) {
        return 1;
    }
    if ( msg == WM_TIMER ) {
        /* fade in during the first 250 ms */
        double alpha = clamp01((GetTickCount() - splashStart) / 250.0);
        SetLayeredWindowAttributes(hwnd, 0, (BYTE) (alpha * 255), LWA_ALPHA);
        InvalidateRect(hwnd, NULL, FALSE);
        return 0;
    }
    if ( msg == WM_APP_STATUS ) {
        InvalidateRect(hwnd, NULL, FALSE);
        return 0;
    }
    if ( msg == WM_APP_READY || msg == WM_APP_FAILED ) {
        PostQuitMessage((int) msg);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

/* rounded corners on Windows 11 (DwmSetWindowAttribute is loaded at run time: not available on old systems) */
static void roundCorners(HWND hwnd) {
    typedef HRESULT(WINAPI * SetAttribute)(HWND, DWORD, LPCVOID, DWORD);
    HMODULE dwm = LoadLibraryW(L"dwmapi.dll");
    if ( dwm != NULL ) {
        SetAttribute set = (SetAttribute) (void *) GetProcAddress(dwm, "DwmSetWindowAttribute");
        if ( set != NULL ) {
            DWORD round = 2; /* DWMWCP_ROUND */
            set(hwnd, 33 /* DWMWA_WINDOW_CORNER_PREFERENCE */, &round, sizeof(round));
        }
    }
}

static void createSplash(HINSTANCE instance) {
    WNDCLASSW wc;
    ZeroMemory(&wc, sizeof(wc));
    wc.style = CS_DROPSHADOW;
    wc.lpfnWndProc = splashProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursor(NULL, IDC_APPSTARTING);
    wc.hIcon = icon;
    wc.lpszClassName = L"BlockCoreSplash";
    RegisterClassW(&wc);
    splashStart = GetTickCount();
    splash = CreateWindowExW(WS_EX_APPWINDOW | WS_EX_LAYERED, L"BlockCoreSplash", L"BlockCore", WS_POPUP,
        (GetSystemMetrics(SM_CXSCREEN) - SPLASH_W) / 2, (GetSystemMetrics(SM_CYSCREEN) - SPLASH_H) / 2, SPLASH_W, SPLASH_H, NULL, NULL,
        instance, NULL);
    SetLayeredWindowAttributes(splash, 0, 0, LWA_ALPHA);
    roundCorners(splash);
    SetTimer(splash, ANIMATION_TIMER, 16, NULL);
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
        return fail(L"The log file cannot be written.");
    }

    swprintf(path, MAX_PATH, L"%ls\\db\\openroberta-db.script", dataDir);
    if ( GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES ) {
        HANDLE create;
        DWORD exitCode = 1;
        setStatus(L"First start: preparing BlockCore \x2026");
        addJavaStart(cmd, L"de.fhg.iais.roberta.main.Administration", FALSE);
        addArg(cmd, L"create-empty-db");
        swprintf(path, MAX_PATH, L"jdbc:hsqldb:file:%ls\\db\\openroberta-db", dataDir);
        addArg(cmd, path);
        create = startHidden(cmd, NULL, log);
        if ( create == NULL ) {
            return fail(L"Java could not be started.");
        }
        WaitForSingleObject(create, INFINITE);
        GetExitCodeProcess(create, &exitCode);
        CloseHandle(create);
        if ( exitCode != 0 ) {
            return fail(L"The database could not be created.");
        }
    }

    /* the class data archive of the Java runtime itself cannot be created when the package is built (on Linux): it is created once here */
    swprintf(path, MAX_PATH, L"%ls\\jre\\bin\\server\\classes.jsa", appDir);
    if ( GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES ) {
        HANDLE dump;
        setStatus(L"First start: preparing Java \x2026");
        cmd[0] = 0;
        swprintf(path, MAX_PATH, L"%ls\\jre\\bin\\java.exe", appDir);
        addArg(cmd, path);
        addArg(cmd, L"-Xshare:dump");
        dump = startHidden(cmd, NULL, log);
        if ( dump != NULL ) {
            WaitForSingleObject(dump, 120000);
            CloseHandle(dump);
        }
    }
    swprintf(path, MAX_PATH, L"%ls\\cds", dataDir);
    CreateDirectoryW(path, NULL);

    setStatus(L"Starting \x2026");
    cmd[0] = 0;
    addJavaStart(cmd, L"de.fhg.iais.roberta.main.ServerStarter", TRUE);
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
    /* installed with the installer (not unzipped): BlockCore may update itself */
    swprintf(path, MAX_PATH, L"%ls\\Uninstall.exe", appDir);
    if ( GetFileAttributesW(path) != INVALID_FILE_ATTRIBUTES ) {
        swprintf(path, MAX_PATH, L"%ls\\update", dataDir);
        CreateDirectoryW(path, NULL);
        addProperty(cmd, L"server.local.update.dir", path);
    }
    {
        /* the server ends normally when the launcher closes this pipe (see stopServer) */
        SECURITY_ATTRIBUTES sa;
        HANDLE inputRead = NULL;
        sa.nLength = sizeof(sa);
        sa.lpSecurityDescriptor = NULL;
        sa.bInheritHandle = TRUE;
        if ( CreatePipe(&inputRead, &serverInput, &sa, 0) ) {
            SetHandleInformation(serverInput, HANDLE_FLAG_INHERIT, 0);
            addProperty(cmd, L"server.local.exitWithStdin", L"true");
        } else {
            inputRead = NULL;
            serverInput = NULL;
        }
        server = startHidden(cmd, inputRead, log);
        if ( inputRead != NULL ) {
            CloseHandle(inputRead);
        }
    }
    free(cmd);
    if ( server == NULL ) {
        return fail(L"Java could not be started.");
    }

    for ( halfSeconds = 0; halfSeconds < 2 * START_TIMEOUT_SECONDS; halfSeconds++ ) {
        if ( WaitForSingleObject(server, 500) == WAIT_OBJECT_0 ) {
            return fail(L"The BlockCore server stopped unexpectedly.");
        }
        if ( portInUse() ) {
            PostMessageW(splash, WM_APP_READY, 0, 0);
            return 0;
        }
    }
    return fail(L"The BlockCore server did not answer in time.");
}

/* stops the server normally: it closes the database and Java writes its class data archive. Killed, if it does not end within 15 s. */
static void stopServer(void) {
    if ( serverInput != NULL ) {
        CloseHandle(serverInput);
        serverInput = NULL;
        if ( server != NULL ) {
            WaitForSingleObject(server, 15000);
        }
    }
    if ( job != NULL ) {
        TerminateJobObject(job, 0);
    }
}

/* ---------------------------------------------------------------- update */

static BOOL CALLBACK closeWindow(HWND hwnd, LPARAM unused) {
    wchar_t title[64];
    wchar_t className[64];
    (void) unused;
    if ( GetClassNameW(hwnd, className, 64) > 0 && wcsncmp(className, L"Chrome_WidgetWin", 16) == 0 && GetWindowTextW(hwnd, title, 64) > 0 &&
        wcsncmp(title, L"BlockCore", 9) == 0 ) {
        PostMessageW(hwnd, WM_CLOSE, 0, 0);
    }
    return TRUE;
}

/* true, if the server stopped to install an update */
static BOOL serverWantsUpdate(void) {
    DWORD exitCode = 0;
    return server != NULL && WaitForSingleObject(server, 0) == WAIT_OBJECT_0 && GetExitCodeProcess(server, &exitCode) &&
        exitCode == EXIT_CODE_UPDATE;
}

/* starts the installer the server downloaded (its path is in update\pending.txt); it must be in the update directory */
static void startUpdate(void) {
    wchar_t pendingPath[MAX_PATH];
    wchar_t installer[MAX_PATH];
    wchar_t updateDir[MAX_PATH];
    wchar_t *cmd;
    char bytes[3 * MAX_PATH];
    DWORD read = 0;
    HANDLE file;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    swprintf(updateDir, MAX_PATH, L"%ls\\update\\", dataDir);
    swprintf(pendingPath, MAX_PATH, L"%lspending.txt", updateDir);
    file = CreateFileW(pendingPath, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if ( file == INVALID_HANDLE_VALUE ) {
        return;
    }
    ReadFile(file, bytes, sizeof(bytes) - 1, &read, NULL);
    CloseHandle(file);
    DeleteFileW(pendingPath);
    bytes[read] = 0;
    if ( MultiByteToWideChar(CP_UTF8, 0, bytes, -1, installer, MAX_PATH) == 0 ) {
        return;
    }
    if ( _wcsnicmp(installer, updateDir, wcslen(updateDir)) != 0 || wcsstr(installer, L"..") != NULL ||
        GetFileAttributesW(installer) == INVALID_FILE_ATTRIBUTES ) {
        showError(L"The update could not be started.");
        return;
    }
    EnumWindows(closeWindow, 0);
    cmd = (wchar_t *) calloc(CMD_MAX, sizeof(wchar_t));
    addArg(cmd, installer);
    wcscat_s(cmd, CMD_MAX, L" /UPDATE");
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    if ( CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, updateDir, &si, &pi) ) {
        CloseHandle(pi.hThread);
        CloseHandle(pi.hProcess);
    } else {
        showError(L"The installer of the update could not be started.");
    }
    free(cmd);
}

/* installers of earlier updates are not needed any more (one that is still running cannot be deleted and stays until the next start) */
static void removeOldInstallers(void) {
    wchar_t pattern[MAX_PATH];
    wchar_t file[MAX_PATH];
    WIN32_FIND_DATAW found;
    HANDLE search;
    swprintf(pattern, MAX_PATH, L"%ls\\update\\BlockCore-Setup-*", dataDir);
    search = FindFirstFileW(pattern, &found);
    if ( search == INVALID_HANDLE_VALUE ) {
        return;
    }
    do {
        swprintf(file, MAX_PATH, L"%ls\\update\\%ls", dataDir, found.cFileName);
        DeleteFileW(file);
    } while ( FindNextFileW(search, &found) );
    FindClose(search);
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
        showError(L"The folder %LOCALAPPDATA% was not found.");
        return 1;
    }
    swprintf(dataDir, MAX_PATH, L"%ls\\BlockCore", localAppData);
    CreateDirectoryW(dataDir, NULL);
    swprintf(path, MAX_PATH, L"%ls\\db", dataDir);
    CreateDirectoryW(path, NULL);
    removeOldInstallers();

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
                if ( serverWantsUpdate() ) {
                    startUpdate();
                    break;
                }
                missing = countBlockCoreWindows() > 0 ? 0 : missing + 1;
            }
        } else {
            MessageBoxW(NULL, L"BlockCore is running (http://127.0.0.1:1999).\n\nClick OK to quit BlockCore.", L"BlockCore",
                MB_OK | MB_ICONINFORMATION);
        }
    } else {
        DestroyWindow(splash);
        openInDefaultBrowser();
        MessageBoxW(NULL, L"BlockCore is running in your browser (http://127.0.0.1:1999).\n\nClick OK to quit BlockCore.", L"BlockCore",
            MB_OK | MB_ICONINFORMATION);
    }

    stopServer();
    if ( instanceMutex != NULL ) {
        CloseHandle(instanceMutex);
    }
    return 0;
}
