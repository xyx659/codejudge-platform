// 单链表 ListNode：结构定义 + LeetCode 格式解析/序列化
// 对应 Java 版 CodeRunner 的 HELPERS_LIST_NODE。学生代码与包装文件共用。
#pragma once
#include "common.h"
#include <unordered_set>

struct ListNode {
    int val;
    ListNode* next;
    ListNode() : val(0), next(nullptr) {}
    ListNode(int v) : val(v), next(nullptr) {}
    ListNode(int v, ListNode* n) : val(v), next(n) {}
};

// 解析 "[1,2,3]" → 单链表
static ListNode* parseListNode(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    ListNode dummy;
    ListNode* cur = &dummy;
    for (auto& e : split_top_level(body)) {
        std::string v = trim(e);
        if (v == "..." || v.empty()) continue;
        cur->next = new ListNode(std::stoi(v));
        cur = cur->next;
    }
    return dummy.next;
}

// 解析带环链表，pos 为环入口下标（-1 表示无环）
static ListNode* parseListNodeWithCycle(const std::string& s, int pos) {
    ListNode* head = parseListNode(s);
    if (head == nullptr || pos < 0) return head;
    ListNode* tail = head;
    ListNode* cycleEntry = nullptr;
    int idx = 0;
    if (idx == pos) cycleEntry = head;
    while (tail->next != nullptr) {
        tail = tail->next;
        idx++;
        if (idx == pos) cycleEntry = tail;
    }
    if (cycleEntry != nullptr) tail->next = cycleEntry;
    return head;
}

// 序列化为 "[1,2,3]"（环安全：用 visited 集合防死循环）
static std::string qlListNode(ListNode* head) {
    std::unordered_set<ListNode*> visited;
    std::string out = "[";
    ListNode* p = head;
    bool first = true;
    while (p != nullptr) {
        if (!first) out += ',';
        first = false;
        out += std::to_string(p->val);
        if (visited.count(p)) break;
        visited.insert(p);
        p = p->next;
    }
    return out + ']';
}
