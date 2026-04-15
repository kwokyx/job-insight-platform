USE career_platform;

ALTER TABLE biz_curriculum_skill_mapping
    DROP FOREIGN KEY fk_curriculum_skill_mapping_label;

ALTER TABLE biz_curriculum_skill_mapping
    ADD CONSTRAINT fk_curriculum_skill_mapping_label
        FOREIGN KEY (skill_id) REFERENCES job_label_dict(id) ON DELETE CASCADE;

ALTER TABLE biz_skill_relation
    DROP FOREIGN KEY fk_skill_relation_label_a;

ALTER TABLE biz_skill_relation
    DROP FOREIGN KEY fk_skill_relation_label_b;

ALTER TABLE biz_skill_relation
    ADD CONSTRAINT fk_skill_relation_label_a
        FOREIGN KEY (skill_id_a) REFERENCES job_label_dict(id) ON DELETE CASCADE;

ALTER TABLE biz_skill_relation
    ADD CONSTRAINT fk_skill_relation_label_b
        FOREIGN KEY (skill_id_b) REFERENCES job_label_dict(id) ON DELETE CASCADE;

DROP TABLE IF EXISTS biz_user_skill;
DROP TABLE IF EXISTS biz_job_skill;
DROP TABLE IF EXISTS biz_skill;
DROP TABLE IF EXISTS biz_user_profile;
DROP TABLE IF EXISTS biz_industry;
