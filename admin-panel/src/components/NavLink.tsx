'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

type NavLinkProps = {
  href: string;
  label: string;
};

export default function NavLink({ href, label }: NavLinkProps) {
  const pathname = usePathname();
  const active = href === '/' ? pathname === '/' : pathname.startsWith(href);
  return (
    <Link
      href={href}
      className={active ? 'rounded bg-gray-900 px-2 py-1 text-white' : 'rounded px-2 py-1 text-gray-700 hover:bg-gray-100'}
    >
      {label}
    </Link>
  );
}
