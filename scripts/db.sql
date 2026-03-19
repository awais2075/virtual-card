-- public.card definition

-- Drop table

-- DROP TABLE public.card;

CREATE TABLE public.card (
	id uuid NOT NULL,
	created_at timestamptz(6) NOT NULL,
	created_by varchar(255) NOT NULL,
	updated_at timestamptz(6) NOT NULL,
	updated_by varchar(255) NOT NULL,
	balance numeric(19, 4) NOT NULL,
	cardholder_name varchar(255) NOT NULL,
	expiry_date date NOT NULL,
	status varchar(255) NOT NULL,
	sub_status varchar(255) NOT NULL,
	"version" int8 NULL,
	CONSTRAINT card_pkey PRIMARY KEY (id),
	CONSTRAINT card_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'BLOCKED'::character varying, 'CLOSED'::character varying, 'EXPIRED'::character varying])::text[]))),
	CONSTRAINT card_sub_status_check CHECK (((sub_status)::text = ANY ((ARRAY['PENDING_REVIEW'::character varying, 'PENDING_APPROVAL'::character varying, 'PENDING_ACTIVATION'::character varying, 'IN_USE'::character varying, 'FROZEN'::character varying, 'FRAUD_REVIEW'::character varying, 'LOST'::character varying, 'STOLEN'::character varying, 'CLOSED_BY_USER'::character varying, 'CLOSED_BY_SYSTEM'::character varying, 'CLOSED_LOST'::character varying, 'CLOSED_STOLEN'::character varying, 'REJECTED'::character varying, 'EXPIRED_NATURAL'::character varying, 'EXPIRED_REPLACED'::character varying])::text[])))
);


-- public."transaction" definition

-- Drop table

-- DROP TABLE public."transaction";

CREATE TABLE public."transaction" (
	id uuid NOT NULL,
	created_at timestamptz(6) NOT NULL,
	created_by varchar(255) NOT NULL,
	updated_at timestamptz(6) NOT NULL,
	updated_by varchar(255) NOT NULL,
	amount numeric(19, 4) NOT NULL,
	balance_after numeric(19, 4) NOT NULL,
	card_id uuid NOT NULL,
	idempotency_key varchar(255) NOT NULL,
	status varchar(255) NOT NULL,
	"type" varchar(255) NOT NULL,
	CONSTRAINT idx_transaction_idempotency_key UNIQUE (idempotency_key),
	CONSTRAINT transaction_pkey PRIMARY KEY (id),
	CONSTRAINT transaction_status_check CHECK (((status)::text = ANY ((ARRAY['SUCCESSFUL'::character varying, 'PENDING'::character varying, 'DECLINED'::character varying])::text[]))),
	CONSTRAINT transaction_type_check CHECK (((type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);
CREATE INDEX idx_transaction_card_id ON public.transaction USING btree (card_id);

-- Import to have Single ACTIVE Card against a USER
CREATE UNIQUE INDEX uidx_card_active_cardholder ON public.card USING btree (cardholder_name) WHERE ((status)::text = 'ACTIVE'::text);