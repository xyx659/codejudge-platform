// C 判题公共工具：输入切分 / 取值 / 类型解析 / LeetCode 格式序列化
// 对应 C++ 版 common.h 与 Java 版 CodeRunner 的 HELPERS_*，供包装文件 main.c 引用。
// 说明：C 无 vector/class，数组统一用「指针 + 长度」表示，函数均为 static（内部链接）。
#pragma once
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

// —— 读入 stdin 全部内容（返回 malloc 的以 \0 结尾的缓冲区）——
static char* read_all_stdin(void) {
    size_t cap = 4096, len = 0;
    char* buf = (char*)malloc(cap);
    if (buf == NULL) return NULL;
    size_t r;
    while ((r = fread(buf + len, 1, cap - len, stdin)) > 0) {
        len += r;
        if (len == cap) {
            cap *= 2;
            char* nb = (char*)realloc(buf, cap);
            if (nb == NULL) { free(buf); return NULL; }
            buf = nb;
        }
    }
    buf[len] = '\0';
    return buf;
}

static char* xstrdup(const char* s) {
    size_t n = strlen(s) + 1;
    char* p = (char*)malloc(n);
    memcpy(p, s, n);
    return p;
}

// —— 基础字符串工具（原地修改，返回指向原缓冲区的指针，不额外分配）——

// 去首尾空白：返回指向首个非空白字符的指针，并把结尾空白截断为 '\0'
static char* trim(char* s) {
    while (*s == ' ' || *s == '\t' || *s == '\r' || *s == '\n') s++;
    if (*s == '\0') return s;
    char* e = s + strlen(s) - 1;
    while (e > s && (*e == ' ' || *e == '\t' || *e == '\r' || *e == '\n')) *e-- = '\0';
    return s;
}

// 顶层切分：按顶层逗号/换行/回车把 s 原地切成若干段（分隔符替换为 '\0'）
// 忽略嵌套 [](){} 与字符串内的分隔符。返回 malloc 的 char**（指向 s 内部），*outCount 为段数。
// 空段（长度 0）被丢弃；仅空白的段会保留，由调用方 trim 后再判断。
static char** split_top_level(char* s, int* outCount) {
    int cap = 8, n = 0;
    char** out = (char**)malloc(sizeof(char*) * cap);
    int depth = 0;
    bool inStr = false;
    char* start = s;
    char* p = s;
    for (;; p++) {
        char c = *p;
        if (c == '\0') break;
        if (c == '"') inStr = !inStr;
        if (!inStr) {
            if (c == '[' || c == '(' || c == '{') depth++;
            else if (c == ']' || c == ')' || c == '}') depth--;
        }
        if (!inStr && depth == 0 && (c == ',' || c == '\n' || c == '\r')) {
            *p = '\0';
            if (p > start) {
                if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
                out[n++] = start;
            }
            start = p + 1;
        }
    }
    if (p > start) {
        if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
        out[n++] = start;
    }
    *outCount = n;
    return out;
}

// 取 "name = value" 的 value 部分（跳过前导空白，返回指向原缓冲区的指针）；无 '=' 时返回整段
static char* value(char* seg) {
    char* eq = strchr(seg, '=');
    char* s = eq ? eq + 1 : seg;
    while (*s == ' ' || *s == '\t' || *s == '\r' || *s == '\n') s++;
    return s;
}

// 查找 pos 参数（环形链表专用），返回 -1 表示无环
static int next_pos_param(char** p, int pc, int idx) {
    for (int i = idx + 1; i < pc; i++) {
        char* seg = p[i];
        while (*seg == ' ' || *seg == '\t' || *seg == '\r' || *seg == '\n') seg++;
        if (strncmp(seg, "pos", 3) == 0) {
            char* v = value(seg);
            if (*v != '\0' && strcmp(v, "null") != 0) return atoi(v);
        }
    }
    return -1;
}

// —— 标量解析 ——
static int parse_int(char* s) { return atoi(trim(s)); }
static long long parse_long(char* s) { return atoll(trim(s)); }
static double parse_double(char* s) { return atof(trim(s)); }
static float parse_float(char* s) { return (float)atof(trim(s)); }
static bool parse_bool(char* s) { return strcmp(trim(s), "true") == 0; }
// 去引号：返回指向原缓冲区的指针（前导引号跳过、结尾引号截断）
static char* parse_string(char* s) {
    char* t = trim(s);
    if (*t == '"') {
        t++;
        char* end = strrchr(t, '"');
        if (end) *end = '\0';
        return t;
    }
    return t;
}

// —— 一维数组解析（返回 malloc 的数组 + 长度）——
static int* parse_int_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    int* r = (int*)malloc(sizeof(int) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = atoi(v);
    }
    free(toks);
    *outSize = cnt;
    return r;
}
static long long* parse_long_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    long long* r = (long long*)malloc(sizeof(long long) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = atoll(v);
    }
    free(toks);
    *outSize = cnt;
    return r;
}
static double* parse_double_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    double* r = (double*)malloc(sizeof(double) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = atof(v);
    }
    free(toks);
    *outSize = cnt;
    return r;
}
static float* parse_float_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    float* r = (float*)malloc(sizeof(float) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = (float)atof(v);
    }
    free(toks);
    *outSize = cnt;
    return r;
}
static bool* parse_bool_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    bool* r = (bool*)malloc(sizeof(bool) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = strcmp(v, "true") == 0;
    }
    free(toks);
    *outSize = cnt;
    return r;
}
// String[] 的每个元素为指向原缓冲区的指针（引号已在原地去掉）
static char** parse_string_array(char* s, int* outSize) {
    *outSize = 0;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    char** r = (char**)malloc(sizeof(char*) * n);
    int cnt = 0;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        r[cnt++] = parse_string(v);
    }
    free(toks);
    *outSize = cnt;
    return r;
}

// —— 二维数组解析（返回 malloc 的行指针数组 + 每行列数）——
static int** parse_int_2d(char* s, int* outRows, int** outCols) {
    *outRows = 0; *outCols = NULL;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** rows = split_top_level(body, &n);
    int** r = (int**)malloc(sizeof(int*) * n);
    int* cols = (int*)malloc(sizeof(int) * n);
    for (int i = 0; i < n; i++) {
        int sz = 0;
        r[i] = parse_int_array(rows[i], &sz);
        cols[i] = sz;
    }
    free(rows);
    *outRows = n; *outCols = cols;
    return r;
}
static double** parse_double_2d(char* s, int* outRows, int** outCols) {
    *outRows = 0; *outCols = NULL;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** rows = split_top_level(body, &n);
    double** r = (double**)malloc(sizeof(double*) * n);
    int* cols = (int*)malloc(sizeof(int) * n);
    for (int i = 0; i < n; i++) {
        int sz = 0;
        r[i] = parse_double_array(rows[i], &sz);
        cols[i] = sz;
    }
    free(rows);
    *outRows = n; *outCols = cols;
    return r;
}
static char*** parse_string_2d(char* s, int* outRows, int** outCols) {
    *outRows = 0; *outCols = NULL;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** rows = split_top_level(body, &n);
    char*** r = (char***)malloc(sizeof(char**) * n);
    int* cols = (int*)malloc(sizeof(int) * n);
    for (int i = 0; i < n; i++) {
        int sz = 0;
        r[i] = parse_string_array(rows[i], &sz);
        cols[i] = sz;
    }
    free(rows);
    *outRows = n; *outCols = cols;
    return r;
}

// —— 设计题：解析 [[],[1],[2,2]] → char***（每次调用的一组原始参数字符串）——
static char*** parse_arg_arrays(char* s, int* outCount, int** outArgCounts) {
    *outCount = 0; *outArgCounts = NULL;
    char* t = trim(s);
    if (*t == '\0' || strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** groups = split_top_level(body, &n);
    char*** r = (char***)malloc(sizeof(char**) * n);
    int* counts = (int*)malloc(sizeof(int) * n);
    for (int i = 0; i < n; i++) {
        char* g = trim(groups[i]);
        if (strcmp(g, "[]") == 0) {
            r[i] = NULL;
            counts[i] = 0;
            continue;
        }
        int glen = (int)strlen(g);
        g[glen - 1] = '\0';
        char* inner = g + 1;
        int m; char** args = split_top_level(inner, &m);
        r[i] = args;
        counts[i] = m;
    }
    free(groups);
    *outCount = n; *outArgCounts = counts;
    return r;
}

// —— 标量序列化（直接打印到 stdout）——
static void print_int(int x) { printf("%d", x); }
static void print_long(long long x) { printf("%lld", x); }
static void print_bool(bool x) { printf("%s", x ? "true" : "false"); }
static void print_string(const char* s) { printf("\"%s\"", s); }
// 浮点：%g 去尾随 0，与 C++ 版 format_double 的 defaultfloat 语义一致（判题侧另有 1e-5 模糊比对兜底）
static void print_double(double d) { printf("%.15g", d); }
static void print_float(float f) { printf("%.15g", (double)f); }

// —— 一维序列化 ——
static void print_int_array(const int* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%d", a[i]); }
    printf("]");
}
static void print_long_array(const long long* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%lld", a[i]); }
    printf("]");
}
static void print_double_array(const double* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%.15g", a[i]); }
    printf("]");
}
static void print_float_array(const float* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%.15g", (double)a[i]); }
    printf("]");
}
static void print_bool_array(const bool* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%s", a[i] ? "true" : "false"); }
    printf("]");
}
static void print_string_array(char* const* a, int n) {
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); print_string(a[i]); }
    printf("]");
}

// —— 二维序列化 ——
static void print_int_2d(int* const* a, int rows, const int* cols) {
    printf("[");
    for (int i = 0; i < rows; i++) { if (i) printf(","); print_int_array(a[i], cols[i]); }
    printf("]");
}
static void print_double_2d(double* const* a, int rows, const int* cols) {
    printf("[");
    for (int i = 0; i < rows; i++) { if (i) printf(","); print_double_array(a[i], cols[i]); }
    printf("]");
}
static void print_string_2d(char* const* const* a, int rows, const int* cols) {
    printf("[");
    for (int i = 0; i < rows; i++) { if (i) printf(","); print_string_array(a[i], cols[i]); }
    printf("]");
}

// —— 简易 BFS 队列（元素为 void*，供树/图解析与序列化共用）——
typedef struct { void** data; int head; int tail; int cap; } Queue;
static void q_init(Queue* q) {
    q->cap = 16;
    q->data = (void**)malloc(sizeof(void*) * q->cap);
    q->head = 0; q->tail = 0;
}
static void q_push(Queue* q, void* v) {
    if (q->tail == q->cap) {
        q->cap *= 2;
        q->data = (void**)realloc(q->data, sizeof(void*) * q->cap);
    }
    q->data[q->tail++] = v;
}
static void* q_pop(Queue* q) { return q->data[q->head++]; }
static bool q_empty(const Queue* q) { return q->head == q->tail; }
static int q_size(const Queue* q) { return q->tail - q->head; }
static void q_free(Queue* q) { free(q->data); }
