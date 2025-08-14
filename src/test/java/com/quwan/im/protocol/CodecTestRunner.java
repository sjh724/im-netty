package com.quwan.im.protocol;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 编解码测试运行器
 * 运行所有编解码相关的测试
 */
public class CodecTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(CodecTestRunner.class);

    @Test
    void runAllCodecTests() throws Exception {
        logger.info("=== 开始运行编解码测试 ===");
        
        // 运行基础功能测试
        BinaryCodecTest basicTest = new BinaryCodecTest();
        basicTest.setUp();
        
        logger.info("1. 测试简单消息编解码");
        basicTest.testEncodeDecodeSimpleMessage();
        
        logger.info("2. 测试IMMessage编解码");
        basicTest.testEncodeDecodeIMMessage();
        
        logger.info("3. 测试群聊消息编解码");
        basicTest.testEncodeDecodeGroupMessage();
        
        logger.info("4. 测试空消息编解码");
        basicTest.testEncodeDecodeEmptyMessage();
        
        logger.info("5. 测试大消息编解码");
        basicTest.testEncodeDecodeLargeMessage();
        
        logger.info("6. 测试Channel流水线");
        basicTest.testChannelPipeline();
        
        // 运行性能测试
        CodecPerformanceTest performanceTest = new CodecPerformanceTest();
        
        logger.info("7. 运行性能对比测试");
        performanceTest.testPerformanceComparison();
        
        logger.info("8. 运行单消息性能测试");
        performanceTest.testSingleMessagePerformance();
        
        logger.info("9. 运行内存使用测试");
        performanceTest.testMemoryUsage();
        
        logger.info("=== 所有编解码测试完成 ===");
    }

    @Test
    void runBasicTestsOnly() throws Exception {
        logger.info("=== 运行基础功能测试 ===");
        
        BinaryCodecTest basicTest = new BinaryCodecTest();
        basicTest.setUp();
        
        basicTest.testEncodeDecodeSimpleMessage();
        basicTest.testEncodeDecodeIMMessage();
        basicTest.testEncodeDecodeGroupMessage();
        basicTest.testEncodeDecodeEmptyMessage();
        basicTest.testChannelPipeline();
        
        logger.info("=== 基础功能测试完成 ===");
    }

    @Test
    void runPerformanceTestsOnly() throws Exception {
        logger.info("=== 运行性能测试 ===");
        
        CodecPerformanceTest performanceTest = new CodecPerformanceTest();
        
        performanceTest.testPerformanceComparison();
        performanceTest.testSingleMessagePerformance();
        performanceTest.testMemoryUsage();
        
        logger.info("=== 性能测试完成 ===");
    }
}
