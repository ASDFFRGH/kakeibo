import { BarChart3, Dices, WalletCards } from "lucide-react";
import Link from "next/link";

export function LedgerNavigation({
  active,
  month,
}: {
  active: "household" | "gambling" | "combined";
  month?: string;
}) {
  const query = month ? `?month=${encodeURIComponent(month)}` : "";

  return (
    <nav className="ledger-navigation" aria-label="台帳の切り替え">
      <Link
        className={active === "household" ? "active" : ""}
        aria-current={active === "household" ? "page" : undefined}
        href={`/${query}`}
      >
        <WalletCards size={18} />
        家計簿
      </Link>
      <Link
        className={active === "gambling" ? "active" : ""}
        aria-current={active === "gambling" ? "page" : undefined}
        href={`/gambling${query}`}
      >
        <Dices size={18} />
        ギャンブル
      </Link>
      <Link
        className={active === "combined" ? "active" : ""}
        aria-current={active === "combined" ? "page" : undefined}
        href={`/combined-summary${query}`}
      >
        <BarChart3 size={18} />
        全体サマリー
      </Link>
    </nav>
  );
}
