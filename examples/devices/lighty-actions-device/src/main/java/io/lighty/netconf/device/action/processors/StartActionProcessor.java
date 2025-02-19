/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.action.processors;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.lighty.codecs.util.SerializationException;
import io.lighty.codecs.util.XmlNodeConverter;
import io.lighty.netconf.device.response.Response;
import io.lighty.netconf.device.response.ResponseData;
import io.lighty.netconf.device.utils.RPCUtil;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import javax.xml.transform.TransformerException;
import org.opendaylight.mdsal.binding.dom.adapter.ConstantAdapterContext;
import org.opendaylight.mdsal.binding.dom.adapter.CurrentAdapterSerializer;
import org.opendaylight.mdsal.binding.dom.codec.spi.BindingDOMCodecServices;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.Device;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.device.Start;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.device.start.Input;
import org.opendaylight.yang.gen.v1.urn.example.data.center.rev180807.device.start.Output;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public class StartActionProcessor extends ActionServiceDeviceProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(StartActionProcessor.class);

    private final CurrentAdapterSerializer adapterSerializer;
    private ActionDefinition actionDefinition;
    private final Start startAction;

    public StartActionProcessor(final Start startAction, final BindingDOMCodecServices codecServices) {
        this.startAction = startAction;
        final ConstantAdapterContext constantAdapterContext = new ConstantAdapterContext(codecServices);
        this.adapterSerializer = constantAdapterContext.currentSerializer();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected CompletableFuture<Response> execute(final Element requestXmlElement,
            final ActionDefinition paramActionDefinition) {
        this.actionDefinition = paramActionDefinition;
        final XmlNodeConverter xmlNodeConverter = getNetconfDeviceServices().getXmlNodeConverter();

        try {
            final XmlElement xmlElement = XmlElement.fromDomElement(requestXmlElement);
            final Element actionElement = findInputElement(xmlElement, this.actionDefinition.getQName());
            final Reader readerFromElement = RPCUtil.createReaderFromElement(actionElement);
            final ContainerNode deserializedNode = (ContainerNode) xmlNodeConverter.deserialize(this.actionDefinition
                    .getInput(), readerFromElement);
            final Input input = this.adapterSerializer.fromNormalizedNodeActionInput(Start.class, deserializedNode);
            final ListenableFuture<RpcResult<Output>> outputFuture = this.startAction.invoke(InstanceIdentifier.create(
                    Device.class), input);
            final CompletableFuture<Response> completableFuture = new CompletableFuture<>();
            Futures.addCallback(outputFuture, new FutureCallback<RpcResult<Output>>() {

                @Override
                public void onSuccess(final RpcResult<Output> result) {
                    final NormalizedNode domOutput = StartActionProcessor.this.adapterSerializer
                            .toNormalizedNodeActionOutput(Start.class, result.getResult());
                    final List<NormalizedNode> list = new ArrayList<>();
                    list.add(domOutput);
                    completableFuture.complete(new ResponseData(list));
                }

                @Override
                public void onFailure(final Throwable throwable) {
                }
            }, Executors.newSingleThreadExecutor());
            return completableFuture;
        } catch (final TransformerException | DocumentedException | SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ActionDefinition getActionDefinition() {
        return this.actionDefinition;
    }
}

