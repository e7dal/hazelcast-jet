/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.pipeline;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.PredicateEx;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.accumulator.LongAccumulator;
import com.hazelcast.jet.core.JetTestSupport;
import com.hazelcast.jet.core.ProcessorMetaSupplier;
import com.hazelcast.jet.core.processor.Processors;
import com.hazelcast.jet.pipeline.test.Assertions;
import com.hazelcast.jet.pipeline.test.ParallelBatchP;
import com.hazelcast.test.HazelcastSerialParametersRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.hazelcast.function.Functions.wholeItem;
import static java.util.stream.Collectors.toList;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(HazelcastSerialParametersRunnerFactory.class)
public class OrderedBatchProcessingTest extends JetTestSupport {

    private static final int LOCAL_PARALLELISM = 11;
    private static Pipeline p;
    private static JetInstance jet;

    @Parameter(value = 0)
    public FunctionEx<BatchStage<Integer>, BatchStage<Integer>> transform;

    @Parameter(value = 1)
    public String transformName;

    @Before
    public void setup() {
        jet = createJetMember();
        p = Pipeline.create();
    }

    @After
    public void after() {
        jet.shutdown();
    }

    @Parameters(name = "{index}: transform={1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                createParamSet(
                        stage -> stage
                                .map(FunctionEx.identity())
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "map"
                ),
                createParamSet(
                        stage -> stage
                                .flatMap(Traversers::singleton)
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "flat-map"
                ),
                createParamSet(
                        stage -> stage
                                .mapUsingIMap("test-map", wholeItem(), (x, ignored) -> x)
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "map-using-imap"
                ),
                createParamSet(
                        stage -> stage
                                .mapUsingReplicatedMap("test-map", wholeItem(), (x, ignored) -> x)
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "map-using-replicated-map"
                ),
                createParamSet(
                        stage -> stage
                                .filter(PredicateEx.alwaysTrue())
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "filter"
                ),
                createParamSet(
                        stage -> stage
                                .mapStateful(LongAccumulator::new, (s, x) -> x)
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "map-stateful-global"
                ),
                createParamSet(
                        stage -> stage
                                .<Integer>customTransform("custom-transform",
                                        Processors.mapP(FunctionEx.identity()))
                                .setLocalParallelism(LOCAL_PARALLELISM),
                        "custom-transform"
                )
        );
    }

    private static Object[] createParamSet(
            FunctionEx<BatchStage<Integer>, BatchStage<Integer>> transform,
            String transformName
    ) {
        return new Object[]{transform, transformName};
    }


    @Test
    public void ordered_batch_processing_test() {
        int itemCount = 5_000;
        List<Integer> sequence1 = IntStream.range(0, itemCount).boxed().collect(toList());
        List<Integer> sequence2 = IntStream.range(itemCount, 2 * itemCount).boxed().collect(toList());
        List<Integer> sequence3 = IntStream.range(2 * itemCount, 3 * itemCount).boxed().collect(toList());
        List<Integer> sequence4 = IntStream.range(3 * itemCount, 4 * itemCount).boxed().collect(toList());

        BatchStage<Integer> srcStage = p.readFrom(
                itemsParallel(Arrays.asList(sequence1, sequence2, sequence3, sequence4))
        );

        BatchStage<Integer> applied = srcStage.apply(transform);

        applied.filter(i -> i < itemCount)
                .apply(Assertions.assertOrdered(sequence1));
        applied.filter(i -> itemCount <= i && i < 2 * itemCount)
                .apply(Assertions.assertOrdered(sequence2));
        applied.filter(i -> 2 * itemCount <= i && i < 3 * itemCount)
                .apply(Assertions.assertOrdered(sequence3));
        applied.filter(i -> 3 * itemCount <= i && i < 4 * itemCount)
                .apply(Assertions.assertOrdered(sequence4));

        p.setPreserveOrder(true);
        jet.newJob(p).join();
    }

    /**
     * Returns a batch source that emits the items supplied in the iterables.
     * It emits the items from different iterables in parallel, but preserves
     * the order within each iterable.
     *
     * @since 4.4
     */
    @Nonnull
    public static <T> BatchSource<T> itemsParallel(@Nonnull List<? extends Iterable<T>> iterables) {
        Objects.requireNonNull(iterables, "iterables");
        return Sources.batchFromProcessor("itemsParallel",
                ProcessorMetaSupplier.of(iterables.size(), () -> new ParallelBatchP<>(iterables))
        );
    }
}
