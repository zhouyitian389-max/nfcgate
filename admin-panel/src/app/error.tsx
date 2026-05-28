'use client';

export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="p-6 space-y-3">
      <h2 className="text-xl font-semibold text-red-700">出错了</h2>
      <p className="text-sm text-gray-600">{error.message || '未知错误'}</p>
      <button className="rounded border px-3 py-2" onClick={reset}>重试</button>
    </div>
  );
}
