CREATE TABLE users (
	user_id UUID,
	full_name VARCHAR(150) NOT NULL,
	email VARCHAR(255) NOT NULL,
	password_hash VARCHAR(255) NOT NULL,
	role VARCHAR(20) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_users PRIMARY KEY (user_id),
	CONSTRAINT uq_users_email UNIQUE (email),
	CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'ADVISOR', 'ADMIN')),
	CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);


CREATE TABLE majors (
	major_id UUID,
	major_name VARCHAR(150) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_majors PRIMARY KEY (major_id),
	CONSTRAINT uq_majors_name UNIQUE (major_name),
	CONSTRAINT chk_majors_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);


CREATE TABLE education_systems (
	education_system_id UUID,
	education_system_name VARCHAR(150) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_education_systems PRIMARY KEY (education_system_id),
	CONSTRAINT uq_education_systems_name UNIQUE (education_system_name),
	CONSTRAINT chk_education_systems_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);


CREATE TABLE cohorts (
	cohort_id UUID,
	cohort_name VARCHAR(100) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_cohorts PRIMARY KEY (cohort_id),
	CONSTRAINT uq_cohorts_name UNIQUE (cohort_name),
	CONSTRAINT chk_cohorts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);


CREATE TABLE departments (
	department_id UUID,
	department_name VARCHAR(150) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_departments PRIMARY KEY (department_id),
	CONSTRAINT uq_departments_name UNIQUE (department_name),
	CONSTRAINT chk_departments_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);


CREATE TABLE student_profiles (
	student_profile_id UUID,
	student_id UUID NOT NULL,
	major_id UUID,
	education_system_id UUID,
	cohort_id UUID,
	note TEXT,

	CONSTRAINT pk_student_profiles PRIMARY KEY (student_profile_id),
	CONSTRAINT uq_student_profiles_student_id UNIQUE (student_id),

	CONSTRAINT fk_student_profiles_student_id
		FOREIGN KEY (student_id)
		REFERENCES users(user_id)
		ON DELETE CASCADE,

	CONSTRAINT fk_student_profiles_major_id
		FOREIGN KEY (major_id)
		REFERENCES majors(major_id)
		ON DELETE RESTRICT,

	CONSTRAINT fk_student_profiles_education_system_id
		FOREIGN KEY (education_system_id)
		REFERENCES education_systems(education_system_id)
		ON DELETE RESTRICT,

	CONSTRAINT fk_student_profiles_cohort_id
		FOREIGN KEY (cohort_id)
		REFERENCES cohorts(cohort_id)
		ON DELETE RESTRICT
);


CREATE TABLE advisor_profiles (
	advisor_profile_id UUID,
	advisor_id UUID NOT NULL,
	department_id UUID NOT NULL,
	phone_number VARCHAR(20),
	note TEXT,

	CONSTRAINT pk_advisor_profiles PRIMARY KEY (advisor_profile_id),
	CONSTRAINT uq_advisor_profiles_advisor_id UNIQUE (advisor_id),

	CONSTRAINT fk_advisor_profiles_advisor_id
		FOREIGN KEY (advisor_id)
		REFERENCES users(user_id)
		ON DELETE CASCADE,

	CONSTRAINT fk_advisor_profiles_department_id
		FOREIGN KEY (department_id)
		REFERENCES departments(department_id)
		ON DELETE RESTRICT
);

CREATE TABLE documents (
	document_id UUID,
	uploaded_by_user_id UUID NOT NULL,
	title VARCHAR(255) NOT NULL,
	description TEXT,
	allow_download BOOLEAN NOT NULL DEFAULT TRUE,
	original_filename VARCHAR(255) NOT NULL,
	storage_path TEXT NOT NULL,
	mime_type VARCHAR(100) NOT NULL,
	size_bytes BIGINT NOT NULL,
	checksum_sha256 CHAR(64) NOT NULL,
	rag_status VARCHAR(20) NOT NULL DEFAULT 'NOT_INDEXED',
	lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
	archived_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_documents PRIMARY KEY (document_id),

	CONSTRAINT fk_documents_uploaded_by_user_id
		FOREIGN KEY (uploaded_by_user_id)
		REFERENCES users(user_id)
		ON DELETE RESTRICT,

	CONSTRAINT uq_documents_storage_path UNIQUE (storage_path),

	CONSTRAINT chk_documents_size_bytes
		CHECK (size_bytes > 0 AND size_bytes <= 20971520),

	CONSTRAINT chk_documents_rag_status
		CHECK (rag_status IN (
			'NOT_INDEXED',
			'PROCESSING',
			'READY',
			'FAILED',
			'DISABLED'
		)),

	CONSTRAINT chk_documents_lifecycle_status
		CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);


CREATE TABLE document_change_requests (
	document_change_request_id UUID,
	document_id UUID NOT NULL,
	requested_by_advisor_id UUID NOT NULL,
	reviewed_by_admin_id UUID,
	request_type VARCHAR(20) NOT NULL,
	status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
	proposed_title VARCHAR(255),
	proposed_description TEXT,
	proposed_allow_download BOOLEAN,
	rejection_reason TEXT,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	reviewed_at TIMESTAMPTZ,

	CONSTRAINT pk_document_change_requests PRIMARY KEY (document_change_request_id),

	CONSTRAINT fk_document_change_requests_document_id
		FOREIGN KEY (document_id)
		REFERENCES documents(document_id)
		ON DELETE RESTRICT,

	CONSTRAINT fk_document_change_requests_requested_by_advisor_id
		FOREIGN KEY (requested_by_advisor_id)
		REFERENCES users(user_id)
		ON DELETE RESTRICT,

	CONSTRAINT fk_document_change_requests_reviewed_by_admin_id
		FOREIGN KEY (reviewed_by_admin_id)
		REFERENCES users(user_id)
		ON DELETE RESTRICT,

	CONSTRAINT chk_document_change_requests_request_type
		CHECK (request_type IN ('ADD', 'UPDATE', 'DELETE')),

	CONSTRAINT chk_document_change_requests_status
		CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);


CREATE TABLE notifications (
	notification_id UUID,
	user_id UUID NOT NULL,
	type VARCHAR(50) NOT NULL,
	title VARCHAR(255) NOT NULL,
	content TEXT NOT NULL,
	related_entity_type VARCHAR(50),
	related_entity_id UUID,
	is_read BOOLEAN NOT NULL DEFAULT FALSE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	read_at TIMESTAMPTZ,

	CONSTRAINT pk_notifications PRIMARY KEY (notification_id),

	CONSTRAINT fk_notifications_user_id
		FOREIGN KEY (user_id)
		REFERENCES users(user_id)
		ON DELETE CASCADE
);


CREATE TABLE audit_logs (
	audit_log_id BIGSERIAL,
	actor_user_id UUID,
	action VARCHAR(100) NOT NULL,
	entity_type VARCHAR(100),
	entity_id UUID,
	metadata JSONB,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	CONSTRAINT pk_audit_logs PRIMARY KEY (audit_log_id),

	CONSTRAINT fk_audit_logs_actor_user_id
		FOREIGN KEY (actor_user_id)
		REFERENCES users(user_id)
		ON DELETE RESTRICT
);