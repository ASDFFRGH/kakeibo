import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "家計簿",
  description: "ローカルで使える家計簿",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ja">
      <body>{children}</body>
    </html>
  );
}
