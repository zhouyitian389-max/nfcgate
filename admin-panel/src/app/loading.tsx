export default function Loading() {
  return (
    <div className="flex items-center justify-center py-20">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-black" />
        <p className="text-sm text-gray-500">加载中...</p>
      </div>
    </div>
  );
}
