package com.somepro.infrastructure.persistence.support;

/**
 * 单据编号生成工具（基础设施层）。
 *
 * 空域申请 KQ-yyyy-####、作业指令 ZY-yyyy-####：前缀 + 年份 + 4 位年内顺序号。
 * 顺序号取「该前缀下当前最大编号 + 1」，由各仓储传入查询到的最大编号，
 * 并发撞唯一索引时仓储重新取号重试。编号只增不复用（含已逻辑删除行）。
 */
public final class DocNoGenerator {

    private DocNoGenerator() {
    }

    /**
     * 生成下一个编号。
     *
     * @param prefix 编号前缀，如 KQ- / ZY-
     * @param year   年份，如 2026
     * @param maxNo  库里该前缀下已用的最大编号（含删除行），null 表示还没有
     * @return 形如 KQ-2026-0101 的编号；年内序号满 9999 会自然进位成 5 位（列宽 32 足够）
     */
    public static String next(String prefix, int year, String maxNo) {
        int seq = 1;
        String head = prefix + year + "-";
        if (maxNo != null && maxNo.startsWith(head)) {
            seq = Integer.parseInt(maxNo.substring(head.length())) + 1;
        }
        return head + String.format("%04d", seq);
    }
}
