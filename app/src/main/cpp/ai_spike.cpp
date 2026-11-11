#include <android/log.h>
#include <jni.h>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <string>
#include <unistd.h>

#include "ggml.h"
#include "logging.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"
#include "llama.h"

// On-device AI engine (D13/D22/D23): load a user-supplied GGUF once, then run grammar-constrained
// one-shot completions against it (no chat loop/streaming — a nightly batch job just needs one
// JSON blob back per note) plus the perf-spike bench used from the debug Settings screen.

constexpr int N_THREADS_MIN = 2;
constexpr int N_THREADS_MAX = 4;
constexpr int N_THREADS_HEADROOM = 2;
constexpr int COMPLETION_CTX = 4096;
constexpr int BATCH_SIZE = 512;

static llama_model *g_model;

extern "C"
JNIEXPORT void JNICALL
Java_com_mindpalace_app_ai_AiEngine_init(JNIEnv *env, jobject, jstring nativeLibDir) {
    llama_log_set(aichat_android_log_callback, nullptr);
    const auto *path = env->GetStringUTFChars(nativeLibDir, nullptr);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(nativeLibDir, path);
    llama_backend_init();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_mindpalace_app_ai_AiEngine_load(JNIEnv *env, jobject, jstring jmodelPath) {
    llama_model_params params = llama_model_default_params();
    const auto *model_path = env->GetStringUTFChars(jmodelPath, nullptr);
    auto *model = llama_model_load_from_file(model_path, params);
    env->ReleaseStringUTFChars(jmodelPath, model_path);
    if (!model) return 1;
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mindpalace_app_ai_AiEngine_benchModel(JNIEnv *env, jobject, jint pp, jint tg, jint pl, jint nr) {
    if (!g_model) {
        return env->NewStringUTF("No model loaded");
    }

    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
            (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = pp;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (!context) {
        return env->NewStringUTF("Failed to create context");
    }

    double pp_avg = 0.0, tg_avg = 0.0;
    for (int nri = 0; nri < nr; nri++) {
        common_batch batch(context);
        for (int i = 0; i < pp; i++) batch.add(0, i, 0, false);
        batch.set_output(batch.size() - 1, true);
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        const auto t_pp_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (int i = 0; i < tg; i++) {
            batch.clear();
            for (int j = 0; j < pl; j++) batch.add(0, i, j, true);
            llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        }
        const auto t_tg_end = ggml_time_us();
        llama_memory_clear(llama_get_memory(context), false);

        const double t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const double t_tg = double(t_tg_end - t_tg_start) / 1000000.0;
        pp_avg += double(pp) / t_pp;
        tg_avg += double(pl * tg) / t_tg;
    }
    llama_free(context);
    pp_avg /= nr;
    tg_avg /= nr;

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));
    const double model_size_gib = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const double model_n_params_b = double(llama_model_n_params(g_model)) / 1e9;

    std::ostringstream result;
    result << std::setprecision(3);
    result << model_desc << " | " << model_size_gib << " GiB | " << model_n_params_b << "B params | "
           << n_threads << " threads\n"
           << "prompt processing: " << pp_avg << " t/s\n"
           << "token generation: " << tg_avg << " t/s";
    return env->NewStringUTF(result.str().c_str());
}

// One-shot, non-streaming, grammar-constrained completion: system+user prompt in, full generated
// text out. No chat history kept across calls — each note is an independent request (D13: "no
// big-picture judgment, fresh guess per note").
extern "C"
JNIEXPORT jstring JNICALL
Java_com_mindpalace_app_ai_AiEngine_runCompletion(
        JNIEnv *env, jobject,
        jstring jsystemPrompt, jstring juserPrompt, jstring jgrammar, jint nPredict) {
    if (!g_model) {
        return env->NewStringUTF("");
    }

    const char *system_c = env->GetStringUTFChars(jsystemPrompt, nullptr);
    const char *user_c = env->GetStringUTFChars(juserPrompt, nullptr);
    const char *grammar_c = env->GetStringUTFChars(jgrammar, nullptr);
    const std::string system_prompt(system_c);
    const std::string user_prompt(user_c);
    const std::string grammar_text(grammar_c);
    env->ReleaseStringUTFChars(jsystemPrompt, system_c);
    env->ReleaseStringUTFChars(juserPrompt, user_c);
    env->ReleaseStringUTFChars(jgrammar, grammar_c);

    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
            (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = COMPLETION_CTX;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (!context) {
        return env->NewStringUTF("");
    }

    auto chat_templates = common_chat_templates_init(g_model, "");
    std::vector<common_chat_msg> chat_msgs;

    common_chat_msg sys_msg;
    sys_msg.role = "system";
    sys_msg.content = system_prompt;
    std::string formatted = common_chat_format_single(chat_templates.get(), chat_msgs, sys_msg, false, false);
    chat_msgs.push_back(sys_msg);

    common_chat_msg user_msg;
    user_msg.role = "user";
    user_msg.content = user_prompt;
    formatted += common_chat_format_single(chat_templates.get(), chat_msgs, user_msg, true, false);

    auto tokens = common_tokenize(context, formatted, true, true);
    const int max_prompt = COMPLETION_CTX - nPredict - 8;
    if ((int) tokens.size() > max_prompt) {
        LOGw("%s: prompt too long (%d tokens), truncating to %d", __func__, (int) tokens.size(), max_prompt);
        tokens.resize(std::max(0, max_prompt));
    }

    common_batch batch(context);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur = std::min((int) tokens.size() - i, BATCH_SIZE);
        batch.clear();
        for (int j = 0; j < cur; j++) {
            const bool want_logit = (i + j == (int) tokens.size() - 1);
            batch.add(tokens[i + j], i + j, 0, want_logit);
        }
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            llama_free(context);
            return env->NewStringUTF("");
        }
    }
    llama_pos pos = (int) tokens.size();

    common_params_sampling sparams;
    sparams.temp = 0.2f;
    if (!grammar_text.empty()) {
        sparams.grammar = common_grammar(COMMON_GRAMMAR_TYPE_USER, grammar_text);
    }
    auto *sampler = common_sampler_init(g_model, sparams);

    std::ostringstream out;
    for (int i = 0; i < nPredict; i++) {
        const llama_token tok = common_sampler_sample(sampler, context, -1);
        common_sampler_accept(sampler, tok, true);
        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), tok)) break;
        out << common_token_to_piece(context, tok);

        batch.clear();
        batch.add(tok, pos, 0, true);
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) break;
        pos++;
    }

    common_sampler_free(sampler);
    llama_free(context);
    return env->NewStringUTF(out.str().c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mindpalace_app_ai_AiEngine_unload(JNIEnv *, jobject) {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mindpalace_app_ai_AiEngine_shutdown(JNIEnv *, jobject) {
    llama_backend_free();
}
