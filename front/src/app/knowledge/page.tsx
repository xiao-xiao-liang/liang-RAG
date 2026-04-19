"use client";

import { useState, useRef } from "react";
import useSWR from "swr";
import { format } from "date-fns";
import { 
  UploadCloud, 
  FileText, 
  SplitSquareHorizontal, 
  Trash2, 
  Database,
  Loader2,
  Layers,
  Inbox
} from "lucide-react";
import { toast } from "sonner";
import { swrFetcher, http, MOCK_USER_ID } from "@/lib/http";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export default function KnowledgePage() {
  const { data: pageData, mutate } = useSWR(
    `/api/document/page?current=1&size=20`,
    swrFetcher,
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
      errorRetryCount: 0
    }
  );
  const documents = pageData?.records || [];

  const [uploading, setUploading] = useState(false);
  const [splittingMap, setSplittingMap] = useState<Record<string, boolean>>({});
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);
    formData.append("uploadUser", MOCK_USER_ID);
    formData.append("title", file.name);
    formData.append("description", "");
    formData.append("knowledgeBaseType", "DOCUMENT_SEARCH");

    setUploading(true);
    try {
      await http.post("/api/document/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      toast.success("上传成功");
      mutate();
    } catch (e) {
      // 错误一般已经在拦截器处理 toast 了
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  };

  const handleSplit = async (id: string) => {
    setSplittingMap((prev) => ({ ...prev, [id]: true }));
    try {
      await http.post(`/api/document/split/${id}`, {});
      toast.success("切分任务已提交后端");
      mutate();
    } catch (e) {
      //
    } finally {
      setSplittingMap((prev) => ({ ...prev, [id]: false }));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await http.delete(`/api/document/${id}`);
      toast.success("删除成功");
      mutate();
    } catch (e) {
      //
    }
  };

  const handleAreaClick = () => {
    if (!uploading) fileInputRef.current?.click();
  };

  const processingCount = documents.filter((d: any) => d.status === 'PROCESSING').length;
  const totalCount = documents.length;

  const renderStatus = (status: string) => {
    switch (status) {
      case "PROCESSING":
        return (
          <div className="flex items-center gap-1.5 text-amber-600 bg-amber-50 dark:bg-amber-500/10 dark:text-amber-400 px-2.5 py-0.5 rounded-full text-xs font-medium border border-amber-200 dark:border-amber-500/20 w-fit">
            <Loader2 className="w-3 h-3 animate-spin" />
            处理中
          </div>
        );
      case "COMPLETED":
        return (
          <div className="flex items-center gap-1.5 text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10 dark:text-emerald-400 px-2.5 py-0.5 rounded-full text-xs font-medium border border-emerald-200 dark:border-emerald-500/20 w-fit">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            已完成
          </div>
        );
      case "FAILED":
        return (
          <div className="flex items-center gap-1.5 text-red-600 bg-red-50 dark:bg-red-500/10 dark:text-red-400 px-2.5 py-0.5 rounded-full text-xs font-medium border border-red-200 dark:border-red-500/20 w-fit">
            <div className="w-1.5 h-1.5 rounded-full bg-red-500" />
            失败
          </div>
        );
      default:
        return <Badge variant="outline">{status || '未知'}</Badge>;
    }
  };

  const getFileIcon = (filename: string) => {
    const ext = filename?.split('.').pop()?.toLowerCase();
    switch (ext) {
      case 'pdf': return <FileText className="w-5 h-5 text-red-500" />;
      case 'doc':
      case 'docx': return <FileText className="w-5 h-5 text-blue-500" />;
      case 'md': return <FileText className="w-5 h-5 text-zinc-600 dark:text-zinc-300" />;
      default: return <FileText className="w-5 h-5 text-muted-foreground" />;
    }
  };

  return (
    <div className="flex-1 h-full overflow-y-auto p-4 sm:p-6 md:p-8 bg-zinc-50/50 dark:bg-zinc-950/50">
      <div className="max-w-5xl mx-auto space-y-6 md:space-y-8 mt-2">
        
        {/* Header Hero Area */}
        <div className="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-indigo-50/80 via-white to-blue-50/50 dark:from-indigo-950/20 dark:via-zinc-900/50 dark:to-blue-900/10 border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm">
          <div className="absolute top-0 right-0 -mt-16 -mr-16 text-indigo-100 dark:text-indigo-900/10 transition-transform duration-700 hover:scale-105">
            <Database className="w-64 h-64 opacity-60" />
          </div>
          
          <div className="relative z-10">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight bg-gradient-to-r from-zinc-900 to-zinc-600 dark:from-zinc-100 dark:to-zinc-400 bg-clip-text text-transparent">
              企业知识资源库
            </h1>
            <p className="text-zinc-500 dark:text-zinc-400 mt-2 max-w-lg leading-relaxed text-sm sm:text-base">
              在这里上传并管理您的私有企业文档。系统会自动为您将其切块并编入高维向量矩阵，打造专属的大脑基座。
            </p>

            {/* Stat Cards */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 sm:gap-4 mt-6 sm:mt-8 max-w-2xl">
              <div className="flex items-center gap-3 sm:gap-4 bg-white/70 dark:bg-zinc-950/40 backdrop-blur-md p-3 sm:p-4 rounded-2xl border border-white dark:border-zinc-800/80 shadow-sm">
                <div className="p-2 sm:p-3 bg-blue-100/80 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-xl">
                  <Layers className="w-4 h-4 sm:w-5 sm:h-5" />
                </div>
                <div>
                  <p className="text-xs sm:text-sm font-medium text-zinc-500 dark:text-zinc-400 mb-0.5">文档总数</p>
                  <p className="text-xl sm:text-2xl font-bold text-zinc-800 dark:text-zinc-200">{totalCount}</p>
                </div>
              </div>
              
              <div className="flex items-center gap-3 sm:gap-4 bg-white/70 dark:bg-zinc-950/40 backdrop-blur-md p-3 sm:p-4 rounded-2xl border border-white dark:border-zinc-800/80 shadow-sm">
                <div className="p-2 sm:p-3 bg-amber-100/80 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 rounded-xl">
                  <Loader2 className={`w-4 h-4 sm:w-5 sm:h-5 ${processingCount > 0 ? "animate-spin" : ""}`} />
                </div>
                <div>
                  <p className="text-xs sm:text-sm font-medium text-zinc-500 dark:text-zinc-400 mb-0.5">正在处理</p>
                  <p className="text-xl sm:text-2xl font-bold text-zinc-800 dark:text-zinc-200">{processingCount}</p>
                </div>
              </div>

              <div className="hidden sm:flex items-center gap-4 bg-white/70 dark:bg-zinc-950/40 backdrop-blur-md p-4 rounded-2xl border border-white dark:border-zinc-800/80 shadow-sm">
                <div className="p-3 bg-emerald-100/80 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-xl">
                  <Database className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-sm font-medium text-zinc-500 dark:text-zinc-400 mb-0.5">引擎状态</p>
                  <p className="text-xl font-bold text-emerald-600 dark:text-emerald-400">运行中</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Upload Dropzone */}
        <div 
          onClick={handleAreaClick}
          className={`relative group flex flex-col items-center justify-center p-8 sm:p-12 border-2 border-dashed rounded-[2rem] transition-all duration-300 cursor-pointer overflow-hidden
            ${uploading 
              ? 'border-blue-300 bg-blue-50/50 dark:border-blue-800/50 dark:bg-blue-900/10' 
              : 'border-zinc-300 hover:border-indigo-400 hover:bg-indigo-50/30 dark:border-zinc-700/80 dark:hover:border-indigo-500/50 dark:hover:bg-indigo-900/10'
            }`}
        >
          <input
            type="file"
            ref={fileInputRef}
            className="hidden"
            onChange={handleFileUpload}
            disabled={uploading}
            accept=".pdf,.md,.doc,.docx,.txt"
          />
          
          <div className="absolute inset-0 bg-gradient-to-b from-transparent to-zinc-50/20 dark:to-zinc-950/20 pointer-events-none" />
          
          <div className="relative z-10 flex flex-col items-center text-center">
            <div className={`p-4 sm:p-5 rounded-2xl mb-4 transition-colors shadow-sm ${uploading ? 'bg-blue-100/80 text-blue-600 dark:bg-blue-900/40 dark:text-blue-400' : 'bg-white text-zinc-500 group-hover:bg-indigo-50 group-hover:text-indigo-600 dark:bg-zinc-800/80 dark:text-zinc-400 dark:group-hover:bg-indigo-900/40 dark:group-hover:text-indigo-400'}`}>
              {uploading ? (
                <Loader2 className="w-8 h-8 sm:w-10 sm:h-10 animate-spin" />
              ) : (
                <UploadCloud className="w-8 h-8 sm:w-10 sm:h-10" />
              )}
            </div>
            <h3 className="text-lg sm:text-xl font-semibold mb-2 text-zinc-800 dark:text-zinc-200">
              {uploading ? "正在安全加密上传..." : "点击或拖拽文件上传"}
            </h3>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mb-5 max-w-sm px-4">
              支持 PDF, Word, Markdown, TXT 格式。确保只上传企业非机密学习物料。
            </p>
            <Button disabled={uploading} variant={uploading ? "secondary" : "default"} className="rounded-full px-8 shadow-sm">
              {uploading ? "请稍候..." : "选择本地文档"}
            </Button>
          </div>
        </div>

        {/* Data List */}
        <Card className="shadow-sm border-zinc-200/80 dark:border-zinc-800/80 rounded-[2rem] overflow-hidden bg-white/50 dark:bg-zinc-950/20 backdrop-blur-sm">
          <CardHeader className="py-5 px-6 sm:px-8 border-b bg-white dark:bg-zinc-900/40">
             <CardTitle className="text-base font-semibold flex items-center gap-2.5 text-zinc-800 dark:text-zinc-200">
               <Layers className="w-5 h-5 text-indigo-500" /> 已编入库文档 
             </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader className="bg-zinc-50/50 dark:bg-zinc-900/20">
                <TableRow className="hover:bg-transparent text-sm">
                  <TableHead className="w-[45%] pl-6 sm:pl-8 h-12">基础信息</TableHead>
                  <TableHead className="h-12 w-28">当前状态</TableHead>
                  <TableHead className="h-12 hidden sm:table-cell">操作人</TableHead>
                  <TableHead className="h-12 hidden md:table-cell w-36">入库时间</TableHead>
                  <TableHead className="text-right pr-6 sm:pr-8 h-12">管理</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {documents.length === 0 ? (
                  <TableRow className="hover:bg-transparent border-none">
                    <TableCell colSpan={5} className="h-[320px]">
                      <div className="flex flex-col items-center justify-center text-center h-full space-y-4">
                        <div className="w-20 h-20 rounded-3xl bg-zinc-100/80 dark:bg-zinc-800/50 flex items-center justify-center mb-2">
                          <Inbox className="w-10 h-10 text-zinc-400/80" />
                        </div>
                        <div>
                          <p className="text-lg font-medium text-zinc-800 dark:text-zinc-200">暂无知识库记录</p>
                          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1 max-w-xs mx-auto">
                            目前没有任何结构化文档，请通过上方区域进行上传来激活知识大脑。
                          </p>
                        </div>
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  documents.map((doc: any) => (
                    <TableRow key={doc.docId || Math.random()} className="group hover:bg-zinc-50 dark:hover:bg-zinc-900/40 transition-colors border-zinc-100 dark:border-zinc-800/60">
                      <TableCell className="pl-6 sm:pl-8 py-4">
                        <div className="flex items-center gap-3.5">
                          <div className="p-2.5 rounded-xl bg-zinc-100/80 dark:bg-zinc-800/80 group-hover:bg-white dark:group-hover:bg-zinc-700 transition-colors shadow-sm">
                            {getFileIcon(doc.docTitle)}
                          </div>
                          <div>
                            <p className="font-medium text-[15px] text-zinc-800 dark:text-zinc-200 line-clamp-1">
                              {doc.docTitle}
                            </p>
                            <div className="flex items-center gap-2 mt-1">
                              <span className="text-[11px] font-medium px-1.5 py-0.5 rounded-md bg-zinc-100 dark:bg-zinc-800 text-zinc-500 dark:text-zinc-400">
                                {doc.knowledgeBaseType === 'DOCUMENT_SEARCH' ? '常规文档' : doc.knowledgeBaseType}
                              </span>
                            </div>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>{renderStatus(doc.status)}</TableCell>
                      <TableCell className="text-sm text-zinc-600 dark:text-zinc-400 hidden sm:table-cell">
                        <div className="flex items-center gap-2">
                          <div className="w-6 h-6 rounded-full bg-indigo-100 dark:bg-indigo-900/60 text-indigo-700 dark:text-indigo-400 flex items-center justify-center font-bold text-xs ring-1 ring-white dark:ring-zinc-950">
                            {doc.uploadUser?.charAt(0).toUpperCase() || 'U'}
                          </div>
                          <span className="truncate max-w-[100px]">{doc.uploadUser}</span>
                        </div>
                      </TableCell>
                      <TableCell className="text-sm text-zinc-500 dark:text-zinc-400 hidden md:table-cell">
                        {doc.createTime ? format(new Date(doc.createTime), 'yyyy/MM/dd') : '-'}
                      </TableCell>
                      <TableCell className="text-right pr-6 sm:pr-8">
                        <div className="flex justify-end gap-1.5 sm:opacity-0 sm:-translate-x-2 sm:group-hover:opacity-100 sm:group-hover:translate-x-0 transition-all duration-300">
                          <Button
                            variant="secondary"
                            size="sm"
                            className="h-8 shadow-none bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-800 dark:hover:bg-zinc-700 text-zinc-700 dark:text-zinc-300 hidden sm:flex"
                            disabled={splittingMap[doc.docId] || doc.status === 'PROCESSING'}
                            onClick={() => handleSplit(doc.docId)}
                            title="重新触发任务"
                          >
                            <SplitSquareHorizontal className="w-3.5 h-3.5 mr-1.5" />
                            重构
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 text-zinc-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10 dark:hover:text-red-400 rounded-lg"
                            onClick={() => handleDelete(doc.docId)}
                          >
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
