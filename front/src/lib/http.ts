import axios from 'axios';
import { toast } from 'sonner';

// 配置后端服务的基础路径（可以在 next.config.js 中配置 rewrite 避免跨域，这里假设以 /api 开始）
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || '';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// Mock user config
export const MOCK_USER_ID = "25";
export const MOCK_USER_NAME = "CaiXuKun";

// 请求拦截器
http.interceptors.request.use(
  (config) => {
    // 若后期有 Token 可在此处注入
    // config.headers['Authorization'] = `Bearer ${token}`
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器
http.interceptors.response.use(
  (response) => {
    const data = response.data;
    // 根据 Result 约定的 success/code 处理
    if (data && data.code !== '0') {
      toast.error(data.message || '请求操作失败');
      return Promise.reject(new Error(data.message || 'Error'));
    }
    return data.data; // 直接剥离并返回内部 data 数据
  },
  (error) => {
    toast.error(error?.response?.data?.message || error.message || '网络请求失败');
    return Promise.reject(error);
  }
);

/**
 * 供 SWR 使用的 Fetcher
 */
export const swrFetcher = async (url: string): Promise<any> => {
  const res = await http.get(url);
  return res;
};
