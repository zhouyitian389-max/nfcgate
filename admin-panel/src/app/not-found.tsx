import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center space-y-4">
        <h1 className="text-6xl font-bold text-gray-300">404</h1>
        <p className="text-lg text-gray-600">页面未找到</p>
        <Link href="/" className="inline-block rounded bg-black px-4 py-2 text-white">
          返回首页
        </Link>
      </div>
    </div>
  );
}
