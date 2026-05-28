'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import LogoutButton from './LogoutButton';

const navItems = [
  { href: '/', label: 'Dashboard' },
  { href: '/sessions', label: 'Sessions' },
  { href: '/users', label: 'Users' },
  { href: '/devices', label: 'Devices' },
  { href: '/cards', label: 'Cards' },
  { href: '/logs', label: 'Logs' },
];

export default function Sidebar() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  const nav = (
    <>
      <h1 className="text-lg font-semibold mb-4">NFC Relay Admin</h1>
      <nav className="flex flex-col gap-1 text-sm">
        {navItems.map(({ href, label }) => {
          const active = pathname === href || (href !== '/' && pathname.startsWith(href));
          return (
            <Link
              key={href}
              href={href}
              onClick={() => setOpen(false)}
              className={`rounded px-2 py-1.5 ${active ? 'bg-gray-100 font-medium text-black' : 'text-gray-600 hover:bg-gray-50'}`}
            >
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="pt-4">
        <LogoutButton />
      </div>
    </>
  );

  return (
    <>
      <button
        className="fixed top-3 left-3 z-50 rounded border bg-white p-2 shadow md:hidden"
        onClick={() => setOpen(!open)}
        aria-label="Toggle menu"
      >
        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          {open
            ? <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            : <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          }
        </svg>
      </button>

      {open && (
        <div className="fixed inset-0 z-40 bg-black/30 md:hidden" onClick={() => setOpen(false)} />
      )}

      <aside className={`
        fixed inset-y-0 left-0 z-40 w-56 bg-white border-r p-4 space-y-2 transform transition-transform duration-200
        md:relative md:translate-x-0
        ${open ? 'translate-x-0' : '-translate-x-full'}
      `}>
        {nav}
      </aside>
    </>
  );
}
