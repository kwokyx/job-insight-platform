# Current Schema Alignment

## Official tables kept

- AI module: `ai_conversation`, `ai_message`, `agent_task`, `agent_task_step`, `agent_tool_call`, `ai_context_memory`, `ai_artifact`, `ai_feedback`
- System module: `sys_user`, `sys_api_key`, `sys_api_call_log`, `sys_operation_log`
- Crawl module: `crawl_task`, `crawl_task_shard`, `crawl_worker`, `crawl_proxy`, `crawl_task_log`, `crawl_job_posting`, `system_config`
- Dimension and analysis module: `dim_region`, `dim_major`, `dim_job_category`, `dim_industry`, `job_label_dict`, `job_label_rel`, `job_welfare_dict`, `job_welfare_rel`, `major_job_match_rule`, `user_profile`, `user_report_preference`
- Business module: `biz_company`, `biz_job_posting`, `biz_job_history`, `biz_analysis_task`, `biz_analysis_report`, `biz_report_snapshot`, `biz_recommendation_result`, `biz_career_path`, `biz_curriculum`, `biz_curriculum_skill_mapping`, `biz_skill_relation`, `biz_data_source`, `biz_employment_indicator`, `biz_notification`, `biz_user_subscription`, `biz_webhook_endpoint`, `biz_webhook_delivery`, `biz_report_schedule`

## Layer split in current database

- `crawl_job_posting`: raw distributed crawl storage, keyed by `url_obj_id`
- `biz_job_posting`: cleaned business-facing job table used by backend APIs
- Current backend code is aligned to `biz_job_posting`

## Legacy duplicates already removed

- `biz_skill`
- `biz_job_skill`
- `biz_user_skill`
- `biz_user_profile`
- `biz_user_job_action`
- `biz_recommend_result`
- `biz_recommend_task`
- `biz_crawl_log`
- `biz_crawl_task`
- `biz_industry`

## Notes

- `dim_industry` is the retained industry dimension table.
- `biz_industry` was removed because it had no code usage, no foreign key dependency, and duplicated the dimension responsibility.
- Skill relations now depend on `job_label_dict` instead of the removed legacy skill tables.
