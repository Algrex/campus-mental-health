import {getJson, post, put} from './http.js';
const base = '/api/v1';
export const api = {
  mlSummary: () => getJson(`${base}/ml/summary`),
  mlTrain: () => post(`${base}/ml/train`),
  mlPredict: (data) => post(`${base}/ml/predict`, data),
  dashboard: () => getJson(`${base}/dashboard`),
  students: (params = {}) =>
    getJson(`${base}/students?${new URLSearchParams(params)}`),
  student: (id) => getJson(`${base}/students/${id}`),
  saveStudent: (data, id) =>
    id ? put(`${base}/students/${id}`, data) : post(`${base}/students`, data),
  importStudents: (csv) => post(`${base}/students/import`, {csv}),
  profile: () => getJson(`${base}/profile`),
  assess: (data) => post(`${base}/assessments`, data),
  checkIn: (data) => post(`${base}/check-ins`, data),
  alerts: () => getJson(`${base}/alerts`),
  alert: (id) => getJson(`${base}/alerts/${id}`),
  transition: (id, action, reason) =>
    post(`${base}/alerts/${id}/${action}`, {reason}),
  intervention: (id, data) => post(`${base}/alerts/${id}/interventions`, data),
  followUps: () => getJson(`${base}/follow-ups`),
  notifications: () => getJson(`${base}/notifications`),
  read: (id) => post(`${base}/notifications/${id}/read`),
};
