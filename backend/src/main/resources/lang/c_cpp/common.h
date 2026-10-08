// C/C++ 判题公共工具：输入切分 / 取值 / 类型解析 / LeetCode 格式序列化
// 对应 Java 版 CodeRunner 的 HELPERS_JSON + HELPERS_BASE，供包装文件 main.cpp / main.c 引用。
#pragma once
#include <string>
#include <vector>
#include <iostream>
#include <sstream>
#include <iomanip>
#include <unordered_set>
#include <unordered_map>

// —— 基础字符串工具 ——
static std::string trim(const std::string& s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}

static std::string unquote(const std::string& s) {
    std::string t = trim(s);
    if (t.size() >= 2 && t.front() == '"' && t.back() == '"') {
        return t.substr(1, t.size() - 2);
    }
    return t;
}

// 顶层切分：按顶层逗号/换行/回车切分，忽略嵌套 [](){} 与字符串内的分隔符
static std::vector<std::string> split_top_level(const std::string& s) {
    std::vector<std::string> out;
    int depth = 0;
    bool inStr = false;
    std::string cur;
    for (char c : s) {
        if (c == '"') inStr = !inStr;
        if (!inStr) {
            if (c == '[' || c == '(' || c == '{') depth++;
            else if (c == ']' || c == ')' || c == '}') depth--;
        }
        if (!inStr && depth == 0 && (c == ',' || c == '\n' || c == '\r')) {
            out.push_back(cur);
            cur.clear();
            continue;
        }
        cur.push_back(c);
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

// 取 "name = value" 的 value 部分；无 '=' 时返回整段
static std::string value(const std::string& seg) {
    std::string s = trim(seg);
    size_t eq = s.find('=');
    if (eq != std::string::npos) return trim(s.substr(eq + 1));
    return s;
}

// 查找 pos 参数（环形链表专用），返回 -1 表示无环
static int next_pos_param(const std::vector<std::string>& p, int currentIdx) {
    for (size_t i = currentIdx + 1; i < p.size(); i++) {
        std::string seg = trim(p[i]);
        if (seg.rfind("pos", 0) == 0) {
            std::string v = value(seg);
            if (!v.empty() && v != "null") return std::stoi(v);
        }
    }
    return -1;
}

// —— 标量解析 ——
static int parse_int(const std::string& s) { return std::stoi(trim(s)); }
static long long parse_long(const std::string& s) { return std::stoll(trim(s)); }
static double parse_double(const std::string& s) { return std::stod(trim(s)); }
static float parse_float(const std::string& s) { return std::stof(trim(s)); }
static bool parse_bool(const std::string& s) { return trim(s) == "true"; }
static std::string parse_string(const std::string& s) { return unquote(s); }

// —— 一维 vector 解析（int[] / long[] / double[] / float[] / bool[] / String[]）——
static std::vector<int> parse_int_vector(const std::string& s) {
    std::vector<int> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(std::stoi(v));
    }
    return out;
}
static std::vector<long long> parse_long_vector(const std::string& s) {
    std::vector<long long> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(std::stoll(v));
    }
    return out;
}
static std::vector<double> parse_double_vector(const std::string& s) {
    std::vector<double> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(std::stod(v));
    }
    return out;
}
static std::vector<float> parse_float_vector(const std::string& s) {
    std::vector<float> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(std::stof(v));
    }
    return out;
}
static std::vector<bool> parse_bool_vector(const std::string& s) {
    std::vector<bool> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(trim(v) == "true");
    }
    return out;
}
static std::vector<std::string> parse_string_vector(const std::string& s) {
    std::vector<std::string> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        out.push_back(unquote(v));
    }
    return out;
}

// —— 二维 vector 解析（int[][] / double[][] 等）——
static std::vector<std::vector<int>> parse_int_2d(const std::string& s) {
    std::vector<std::vector<int>> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        out.push_back(parse_int_vector(trim(e)));
    }
    return out;
}
static std::vector<std::vector<double>> parse_double_2d(const std::string& s) {
    std::vector<std::vector<double>> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        out.push_back(parse_double_vector(trim(e)));
    }
    return out;
}
static std::vector<std::vector<std::string>> parse_string_2d(const std::string& s) {
    std::vector<std::vector<std::string>> out;
    std::string t = trim(s);
    if (t == "[]" || t.empty()) return out;
    std::string body = t.substr(1, t.size() - 2);
    for (auto& e : split_top_level(body)) {
        out.push_back(parse_string_vector(trim(e)));
    }
    return out;
}

// —— 标量序列化 ——
static std::string serialize_int(int x) { return std::to_string(x); }
static std::string serialize_long(long long x) { return std::to_string(x); }
static std::string serialize_bool(bool x) { return x ? "true" : "false"; }
static std::string serialize_string(const std::string& s) { return "\"" + s + "\""; }

// 浮点：去掉无意义的尾随 0，尽量贴近 Java Double.toString（判题侧另有 1e-5 模糊比对兜底）
static std::string format_double(double d) {
    std::ostringstream oss;
    oss << std::setprecision(15) << d;
    std::string s = oss.str();
    if (s.find('.') != std::string::npos) {
        while (!s.empty() && s.back() == '0') s.pop_back();
        if (!s.empty() && s.back() == '.') s.pop_back();
    }
    return s.empty() ? "0" : s;
}
static std::string serialize_double(double d) { return format_double(d); }
static std::string serialize_float(float f) { return format_double((double)f); }

// —— 一维序列化 ——
static std::string serialize_int_vector(const std::vector<int>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += std::to_string(v[i]); }
    return out + ']';
}
static std::string serialize_long_vector(const std::vector<long long>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += std::to_string(v[i]); }
    return out + ']';
}
static std::string serialize_double_vector(const std::vector<double>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += format_double(v[i]); }
    return out + ']';
}
static std::string serialize_float_vector(const std::vector<float>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += format_double((double)v[i]); }
    return out + ']';
}
static std::string serialize_bool_vector(const std::vector<bool>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += (v[i] ? "true" : "false"); }
    return out + ']';
}
static std::string serialize_string_vector(const std::vector<std::string>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += '"' + v[i] + '"'; }
    return out + ']';
}

// —— 二维序列化 ——
static std::string serialize_int_2d(const std::vector<std::vector<int>>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += serialize_int_vector(v[i]); }
    return out + ']';
}

// —— 设计题解析（对应 Java HELPERS_JSON_DESIGN 的 parseStringArray / parseArgArrays）——
// 解析一组参数 "1,2" → ["1","2"]（嵌套数组保留为单个 token）
static std::vector<std::string> parse_inner_args(const std::string& s) {
    std::string t = trim(s);
    if (t.empty()) return {};
    std::vector<std::string> out;
    for (auto& e : split_top_level(t)) {
        std::string v = trim(e);
        if (!v.empty()) out.push_back(v);
    }
    return out;
}

// 解析设计题第二行 [[],[1],[2,2]] → vector<vector<string>>（原始参数字符串，不解析类型）
static std::vector<std::vector<std::string>> parse_arg_arrays(const std::string& s) {
    std::vector<std::vector<std::string>> result;
    std::string t = trim(s);
    if (t == "[]") return result;
    std::string body = t.substr(1, t.size() - 2);
    int depth = 0;
    bool inStr = false;
    std::string cur;
    for (char c : body) {
        if (c == '"') inStr = !inStr;
        if (!inStr) {
            if (c == '[') { depth++; if (depth == 1) { cur.clear(); continue; } }
            else if (c == ']') { depth--; if (depth == 0) { result.push_back(parse_inner_args(cur)); continue; } }
        }
        if (depth >= 1) cur.push_back(c);
    }
    return result;
}
static std::string serialize_double_2d(const std::vector<std::vector<double>>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += serialize_double_vector(v[i]); }
    return out + ']';
}
static std::string serialize_string_2d(const std::vector<std::vector<std::string>>& v) {
    std::string out = "[";
    for (size_t i = 0; i < v.size(); i++) { if (i) out += ','; out += serialize_string_vector(v[i]); }
    return out + ']';
}
