# Apifox 后端联调指南

本指南用于当前后端优先阶段的手工联调。先使用 Apifox 跑通 HTTP、JWT、上传、异步 AI 任务与 embedding 回填；不要把模型回调接口当作普通用户接口手工调用。

可直接导入 [Apifox 集合](apifox/clip-backend-debug.collection.json)。导入后仅需设置集合变量：`baseUrl`、`username`、`password`，并在“分类上传”中选择一个本地图片文件。

## 前置条件

- 后端运行在 `{{baseUrl}}`，本地默认值为 `http://127.0.0.1:8080`。
- 模型服务运行在 `http://127.0.0.1:5000`；真实 Chroma/embedding 验证必须关闭 `CLIP_SMOKE_MODE`。
- 导入的 `schema.sql` 与后端、模型配置中的 MySQL 和 callback token 一致。
- 用户侧接口除注册和登录外都需要 `Authorization: Bearer {{jwt}}`。

所有 Java API 使用统一包裹：`code=1` 表示成功，业务数据位于 `data`，失败原因位于 `message`。

## 推荐联调顺序

1. **注册**：`POST /user/register`。如用户已存在，可跳过。
2. **登录**：`POST /user/login`。集合中的测试脚本会把 `data.token` 保存为 `jwt`。
3. **分类上传**：`POST /user/category/upload`，使用 `multipart/form-data`：
   - `username`：集合变量 `{{username}}`；
   - `descriptionList`：JSON 数组字符串，例如 `["cat","dog"]`；
   - `photoList`：选择一个或多个实际图片文件。
   成功后会创建 `CATEGORY` 任务并写入活跃 `photos`，这是 backfill 的数据来源。
4. **文本搜索**：`POST /user/match/upload`，提交 `username` 和 `description`。响应 `data` 是 `taskId`；用 `GET /user/match/download?username=...&taskId=...` 读取结果。
5. **Embedding 回填**：`POST /user/embeddings/backfill`。
   - 只传 `username`：回填该用户全部活跃图片；
   - 传 `photoIds`：仅回填指定的、属于该用户且仍为 `ACTIVE` 的图片。
   响应 `data.taskId` 和 `data.photoCount`。真实模型模式下任务应经历 `PENDING → RUNNING → SUCCESS`，并由模型回调写入 `embedding_records`。
6. **观察任务**：使用 JWT 调用 `GET /user/ai-tasks/{taskId}`；接口只返回当前认证用户拥有的任务。
7. **观察向量记录**：使用 JWT 调用 `GET /user/embeddings?status=READY&embeddingModel=clip-vit-l-14&page=1&pageSize=20`。

## 回填请求与预期结果

```http
POST {{baseUrl}}/user/embeddings/backfill
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "username": "{{username}}"
}
```

成功响应示例：

```json
{
  "code": 1,
  "message": "success",
  "data": {
    "taskId": 123,
    "photoCount": 2,
    "msg": "Embedding backfill task accepted"
  }
}
```

针对指定图片的请求示例：

```json
{
  "username": "{{username}}",
  "photoIds": [11, 12]
}
```

若某个图片已删除、不属于该用户或不存在，后端会拒绝整个请求且不会请求 Python 模型服务。

## 不要在 Apifox 手工调用的接口

- `POST /embeddings/backfill` 是 Java 到 Python 的内部任务载荷，不是用户 API。
- `POST /ai/tasks/{taskId}/matches` 与 `POST /ai/tasks/{taskId}/embeddings` 是模型回调接口。若配置了 `AI_CALLBACK_TOKEN`，必须带 `X-AI-Callback-Token`；回填回调还会验证任务类型与 task-photo 归属。
- `GET /actuator/prometheus` 与模型 `GET /metrics` 是受保护的内部监控端点，不应加入 Apifox 公共客户端集合。

要观察异步状态，请查询 MySQL，而不是伪造成功回调：

```sql
SELECT id, task_type, status, error_message, created_at, updated_at
FROM ai_tasks
WHERE user_id = <user_id>
ORDER BY id DESC;

SELECT target_type, target_id, embedding_model, vector_db, collection_name,
       vector_id, dim, status, updated_at
FROM embedding_records
WHERE user_id = <user_id>
ORDER BY target_id;
```

## 常见问题

- **403**：登录后没有把 `data.token` 放入 `Authorization: Bearer {{jwt}}`，或 token 已过期。
- **回填提示没有有效图片**：先完成至少一次真实图片的分类上传；软删除图片不会参与回填。
- **任务 `FAILED` 且错误为 real CLIP mode**：当前使用了 `CLIP_SMOKE_MODE=true`；关闭它后重启模型服务。
- **任务长期 `RUNNING`**：检查模型服务日志、`BACKEND_BASE_URL`、`AI_CALLBACK_TOKEN` 和 `/health`。
